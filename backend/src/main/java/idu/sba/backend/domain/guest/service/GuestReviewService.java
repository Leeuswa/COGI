package idu.sba.backend.domain.guest.service;




import idu.sba.backend.domain.guest.dto.GuestReviewRequest;
import idu.sba.backend.domain.guest.dto.GuestReviewResponse;
import idu.sba.backend.domain.guest.dto.ReviewComment;
import idu.sba.backend.global.ai.AiModel;
import idu.sba.backend.global.ai.AiReviewClient;
import idu.sba.backend.global.ai.AiReviewResult;
import idu.sba.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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

    // 비로그인 체험 제한 횟수(3회). 한 곳에서만 관리하려고 상수로.
    private static final int TRIAL_LIMIT = 3;

    // 자정 초기화 기준 시간대. 서버가 UTC라도 한국 자정에 맞추려고 명시.
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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

        // 처음 만들어진 카운터(값 1)라면 "다음 자정까지" 살아있도록 수명 설정.
        // 이렇게 하면 24시간 고정이 아니라, 매일 자정에 리셋된다.
        if (current == 1L) {
            redisTemplate.expire(countKey, secondsUntilMidnight());
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
                    AiModel.GEMINI_FLASH_LITE, systemPrompt, request.getCode(), request.getLanguage());
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

        // Redis에 저장 (다음 자정까지 보관)
        String reviewKey = "guest:review:" + guestToken + ":" + reviewId;
        try {
            String json = objectMapper.writeValueAsString(response);   // 객체 → JSON 문자열
            redisTemplate.opsForValue().set(reviewKey, json, secondsUntilMidnight());
        } catch (Exception e) {
            throw new RuntimeException("게스트 리뷰 저장 실패", e);
        }

        return response;
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

//    지금부터 "다음 자정까지 남은 시간. 카운터·리뷰 수명에 사용.
    private Duration secondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now(KST);
        LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(); // 내일 00:00
        return Duration.between(now, nextMidnight);
    }

    // 3회 초과 시 던지는 예외. Controller가 잡아 403으로 응답.
    public static class TrialLimitExceededException extends RuntimeException {
        public TrialLimitExceededException(String message) {
            super(message);
        }
    }
}