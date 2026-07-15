package idu.sba.backend.domain.guest.service;




import idu.sba.backend.domain.guest.dto.GuestReviewRequest;
import idu.sba.backend.domain.guest.dto.GuestReviewResponse;
import idu.sba.backend.domain.guest.dto.ReviewComment;
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


        String summary = "더미 리뷰 결과입니다. (AI 연동 전 임시 응답 / level=" + level + ")";
        List<ReviewComment> comments = List.of(
                new ReviewComment("BUG", "CRITICAL", "user.ts", 88, "null 체크 누락 — 옵셔널 체이닝 또는 얼리 리턴 권장"),
                new ReviewComment("CONVENTION", "MINOR", "date.ts", 12, "네이밍 컨벤션 — camelCase 통일 권장")
        );

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

    private String buildPrompt(String code, String language, ReviewLevel level) {
        return "";
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