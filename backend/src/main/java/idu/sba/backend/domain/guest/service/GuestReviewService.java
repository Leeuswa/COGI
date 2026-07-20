package idu.sba.backend.domain.guest.service;




import idu.sba.backend.domain.guest.dto.GuestReviewRecord;
import idu.sba.backend.domain.guest.dto.GuestReviewRequest;
import idu.sba.backend.domain.guest.dto.GuestReviewResponse;
import idu.sba.backend.domain.guest.dto.ReviewComment;
import idu.sba.backend.domain.review.entity.IssueCategory;
import idu.sba.backend.domain.review.entity.IssueSeverity;
import idu.sba.backend.domain.review.entity.Review;
import idu.sba.backend.domain.review.entity.ReviewIssue;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewRepository;
import idu.sba.backend.global.ai.AiInputType;
import idu.sba.backend.global.ai.AiModel;
import idu.sba.backend.global.ai.AiReviewClient;
import idu.sba.backend.global.ai.AiReviewResult;
import idu.sba.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


@Service

@RequiredArgsConstructor
public class GuestReviewService {

    // Redis 리모컨 (카운트·저장에 사용)
    private final StringRedisTemplate redisTemplate;

    // 객체 , JSON 문자열 변환기 (Redis에 문자열로 저장하려고 사용)
    private final ObjectMapper objectMapper;

    // 실제 AI 리뷰 API 호출을 위임할 클라이언트 (DI로 주입)
    private final AiReviewClient aiReviewClient;

    // 회원가입 claim 시 게스트 리뷰를 reviews/review_issues 테이블로 옮기는 데 사용
    private final ReviewRepository reviewRepository;
    private final ReviewIssueRepository reviewIssueRepository;

    // 비로그인 체험 제한 횟수(3회). 한 곳에서만 관리하려고 상수로.
    private static final int TRIAL_LIMIT = 3;

    // 게스트 체험이 쓰는 모델. claim 시 reviews.model_name 에도 이 값을 남긴다.
    private static final AiModel GUEST_MODEL = AiModel.GEMINI_FLASH;

    // 체험 횟수 유지 창. 첫 리뷰 시점부터 24시간 뒤 카운터가 만료돼 3회가 리셋된다.
    private static final Duration TRIAL_WINDOW = Duration.ofHours(24);

    public enum ReviewLevel {
        BEGINNER, INTERMEDIATE, ADVANCED
    }

    public GuestReviewResponse createReview(GuestReviewRequest request, String guestToken) {

        // "guest:count:토큰" 이름표로 카운터 생성
        String countKey = "guest:count:" + guestToken;

        // increment: 값을 1 올리고 "올린 뒤 현재값"을 반환.
        Long count = redisTemplate.opsForValue().increment(countKey);

        // increment는 이론상 null 가능 → 방어. null이면 1로 취급.
        long current = (count == null) ? 1L : count;

        // 첫 리뷰(값 1)일 때만 24시간 수명 설정. 이후엔 갱신하지 않으므로
        // 첫 리뷰 시점부터 정확히 24시간 뒤에 카운터가 만료되고 3회가 리셋된다.
        if (current == 1L) {
            redisTemplate.expire(countKey, TRIAL_WINDOW);
        }

        // 3회 초과(4번째부터)예외처리 Controller가 이걸 403으로 바꿈
        if (current > TRIAL_LIMIT) {
            throw new TrialLimitExceededException("무료 체험 " + TRIAL_LIMIT + "회를 모두 사용했습니다.");
        }


        ReviewLevel level = ReviewLevel.BEGINNER;

        String summary;
        List<ReviewComment> comments;
        try {
            String systemPrompt = buildPrompt(request.getLanguage(), level);
            AiReviewResult result = aiReviewClient.review(
                    GUEST_MODEL, systemPrompt, request.getCode(), request.getLanguage(), AiInputType.PASTED_CODE);
            summary = result.summary();
            comments = result.issues().stream()
                    .map(i -> new ReviewComment(i.category(), i.severity(), i.filePath(), i.lineNumber(), i.description()))
                    .toList();
        } catch (BusinessException e) {
            // AI 호출 실패 시 방금 올린 체험 횟수를 롤백하고, 에러코드(AI_MODEL_CALL_FAILED)는 그대로 유지한 채 재던짐
            // — GlobalExceptionHandler가 502로 통일 응답하도록(예전처럼 500 RuntimeException으로 뭉개지 않음)
            redisTemplate.opsForValue().decrement(countKey);
            throw e;
        }

        String reviewId = "G-" + UUID.randomUUID();  // 리뷰마다 고유 이름표
        GuestReviewResponse response = new GuestReviewResponse(reviewId, summary, comments);

        // Redis에 저장 (24시간 보관). 회원가입 claim 때 reviews 행을 복원하려고
        // 프론트 응답과 달리 원본 code/language/model 까지 함께 담는다.
        GuestReviewRecord record = new GuestReviewRecord(
                reviewId, summary, comments, request.getCode(), request.getLanguage(), GUEST_MODEL.id());
        String reviewKey = "guest:review:" + guestToken + ":" + reviewId;
        try {
            String json = objectMapper.writeValueAsString(record);   // 객체 → JSON 문자열
            redisTemplate.opsForValue().set(reviewKey, json, TRIAL_WINDOW);
        } catch (Exception e) {
            throw new RuntimeException("게스트 리뷰 저장 실패", e);
        }

        return response;
    }

    // 회원가입/로그인 직후 호출 — 쿠키의 게스트 리뷰를 방금 로그인한 사용자의 reviews 테이블로 옮긴다.
    // best-effort: 로그인/쿠키/식별자가 없거나 이미 옮겨졌으면 조용히 넘어간다(로그인 흐름을 막지 않음).
    @Transactional
    public void claimReview(Long userId, String guestToken, String reviewId) {
        if (userId == null || guestToken == null || reviewId == null) {
            return;
        }
        String reviewKey = "guest:review:" + guestToken + ":" + reviewId;
        String json = redisTemplate.opsForValue().get(reviewKey);
        if (json == null) {
            return; // 이미 claim 됐거나 24시간 만료됨
        }

        GuestReviewRecord record;
        try {
            record = objectMapper.readValue(json, GuestReviewRecord.class);
        } catch (Exception e) {
            throw new RuntimeException("게스트 리뷰 복원 실패", e);
        }

        // reviews 행 생성 (GUEST 타입, 이미 리뷰가 끝난 결과라 COMPLETED)
        Review review = Review.createFromGuest(userId, record.getCode(), record.getLanguage(),
                record.getModelName(), guestToken);
        reviewRepository.save(review);

        // 코멘트 → review_issues. 알 수 없는 category/severity 는 그 건만 건너뛴다.
        List<ReviewIssue> issues = record.getComments().stream()
                .map(c -> toIssueEntity(review.getId(), c))
                .filter(Objects::nonNull)
                .toList();
        reviewIssueRepository.saveAll(issues);

        // 중복 claim 방지 — 옮긴 원본은 삭제
        redisTemplate.delete(reviewKey);
    }

    private ReviewIssue toIssueEntity(Long reviewId, ReviewComment c) {
        try {
            return ReviewIssue.of(reviewId,
                    IssueCategory.valueOf(c.getCategory()),
                    IssueSeverity.valueOf(c.getSeverity()),
                    c.getFilePath(), c.getLineNumber(), c.getDescription());
        } catch (IllegalArgumentException e) {
            return null; // AI가 스키마 밖 값을 준 이슈 — 스킵
        }
    }

    // 시스템 프롬프트만 만든다 — 코드 본문은 AiReviewClient가 별도 user 메시지로 넣는다.
    // "issues" 키/필드명은 AiReviewClientImpl의 공용 파싱 계약과 반드시 일치해야 함.
    private String buildPrompt(String language, ReviewLevel level) {
        return """
                당신은 %s 수준 개발자를 위한 코드 리뷰어입니다.
                이어지는 %s 코드를 리뷰하고, 다른 텍스트 없이 반드시 다음 JSON 형식으로만 답하세요:
                {"summary":"전체 요약 한두 문장",
                 "issues":[{"category":"BUG|PERFORMANCE|CODE_SMELL|CONVENTION|SECURITY",
                            "severity":"CRITICAL|MAJOR|MINOR",
                            "filePath":"파일명(모르면 input)",
                            "lineNumber":1,
                            "description":"문제와 개선 방법"}]}
                """.formatted(level.name(), language);
    }

    // 3회 초과 시 던지는 예외. Controller가 잡아 403으로 응답.
    public static class TrialLimitExceededException extends RuntimeException {
        public TrialLimitExceededException(String message) {
            super(message);
        }
    }
}