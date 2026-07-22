package idu.sba.backend.domain.guest.service;




import idu.sba.backend.domain.guest.dto.GuestReviewRecord;
import idu.sba.backend.domain.guest.dto.GuestReviewRequest;
import idu.sba.backend.domain.guest.dto.GuestReviewResponse;
import idu.sba.backend.domain.guest.dto.ReviewComment;
import idu.sba.backend.domain.review.entity.*;
import idu.sba.backend.domain.review.repository.AiUsageLogRepository;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewRepository;
import idu.sba.backend.domain.review.service.PromptBuilder;
import idu.sba.backend.domain.user.entity.Level;
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

    // 로그인 리뷰와 동일한 프롬프트 로더 재사용 — resources/prompts 의 레벨/플랜별 지침을 조합
    private final PromptBuilder promptBuilder;

    // 비로그인 체험 제한 횟수(3회). 한 곳에서만 관리하려고 상수로.
    private static final int TRIAL_LIMIT = 3;

    // 게스트 체험이 쓰는 모델. claim 시 reviews.model_name 에도 이 값을 남긴다.
    private static final AiModel GUEST_MODEL = AiModel.GEMINI_FLASH;

    // 체험 횟수 유지 창. 첫 리뷰 시점부터 24시간 뒤 카운터가 만료돼 3회가 리셋된다.
    private static final Duration TRIAL_WINDOW = Duration.ofHours(24);

    // 게스트는 항상 무료 등급 — prompt_plan_free.txt 를 쓴다.
    private static final String GUEST_PLAN = "FREE";


    //Admin관리자 창에서 Ai사용량을 표기해야
    private final AiUsageLogRepository aiUsageLogRepository;

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


        Level level = parseLevel(request.getLevel());

        String summary;
        List<ReviewComment> comments;
        try {
            String systemPrompt = promptBuilder.build(level, GUEST_PLAN);
            AiReviewResult result = aiReviewClient.review(
                    GUEST_MODEL, systemPrompt, request.getCode(), null, AiInputType.PASTED_CODE);
            summary = result.summary();
            comments = result.issues().stream()
                    .map(i -> new ReviewComment(i.category(), i.severity(), i.filePath(), i.lineNumber(), i.description()))
                    .toList();
            //게스트 AI 사용량 기록 — userId=null(비로그인), 통계는 부차적이라 실패해도 리뷰는 계속
            try {
                aiUsageLogRepository.save(AiUsageLog.of(
                        null, GUEST_MODEL.id(), result.inputTokens(), result.outputTokens(),
                        result.cost(), "GUEST_REVIEW"));
            } catch (Exception logEx) {
                // 로깅만 하고 삼킴 — 회계 저장 실패가 게스트 리뷰를 막으면 안 됨
            }
        } catch (BusinessException e) {
            // AI 호출 실패 시 방금 올린 체험 횟수를 롤백하고, 에러코드(AI_MODEL_CALL_FAILED)는 그대로 유지한 채 재던짐
            // — GlobalExceptionHandler가 502로 통일 응답하도록(예전처럼 500 RuntimeException으로 뭉개지 않음)
            redisTemplate.opsForValue().decrement(countKey);
            throw e;
        }

        String reviewId = "G-" + UUID.randomUUID();  // 리뷰마다 고유 이름표
        GuestReviewResponse response = new GuestReviewResponse(reviewId, summary, comments);

        // Redis에 저장 (24시간 보관). 회원가입 claim 때 reviews 행을 복원하려고
        // 프론트 응답과 달리 원본 code/model 까지 함께 담는다. (게스트는 언어를 받지 않아 language는 null)
        GuestReviewRecord record = new GuestReviewRecord(
                reviewId, summary, comments, request.getCode(), null, GUEST_MODEL.id());
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

    // 프론트가 보낸 레벨 문자열 → Level. null이거나 스키마 밖 값이면 BEGINNER로 안전 폴백.
    private Level parseLevel(String raw) {
        try {
            return Level.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Level.BEGINNER;
        }
    }

    // 3회 초과 시 던지는 예외. Controller가 잡아 403으로 응답.
    public static class TrialLimitExceededException extends RuntimeException {
        public TrialLimitExceededException(String message) {
            super(message);
        }
    }

}