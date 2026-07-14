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

// @Service : 이 클래스는 서비스(핵심 로직)라고 스프링에 등록. Controller가 가져다 씀.
@Service
// @RequiredArgsConstructor : 아래 final 필드(redisTemplate, objectMapper)를 채우는 생성자를 자동 생성 → 스프링이 주입.
@RequiredArgsConstructor
public class GuestReviewService {

    // Redis 리모컨 (카운트·저장에 사용)
    private final StringRedisTemplate redisTemplate;

    // 객체 <-> JSON 문자열 변환기 (Redis에 문자열로 저장하려고 사용)
    private final ObjectMapper objectMapper;

    // 비로그인 체험 제한 횟수(3회). 한 곳에서만 관리하려고 상수로.
    private static final int TRIAL_LIMIT = 3;

    // 자정 초기화 기준 시간대. 서버가 UTC라도 한국 자정에 맞추려고 명시.
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 리뷰 수준. 비로그인은 기본 초급(BEGINNER)을 쓰지만,
     * 나중에 초급/중급/고급을 고를 수 있게 파라미터로 받을 수 있도록 enum으로 준비.
     * (관리자 review_guidelines의 BEGINNER/INTERMEDIATE/ADVANCED 와 동일 체계)
     */
    public enum ReviewLevel {
        BEGINNER, INTERMEDIATE, ADVANCED
    }

    /**
     * 비로그인 리뷰 생성 (API-039)
     * @param request    프론트가 보낸 코드/언어
     * @param guestToken 쿠키에서 꺼낸 게스트 식별값 (Controller가 넘겨줌)
     * @return 리뷰 결과(응답 DTO)
     */
    public GuestReviewResponse createReview(GuestReviewRequest request, String guestToken) {

        // ── 1단계: 3회 제한 체크 ─────────────────────────────
        // "guest:count:토큰" 이름표로 카운터 생성. (count 뒤 콜론 주의!)
        String countKey = "guest:count:" + guestToken;

        // increment: 값을 1 올리고 "올린 뒤 현재값"을 반환. 처음이면 1, 다음이면 2...
        Long count = redisTemplate.opsForValue().increment(countKey);

        // increment는 이론상 null 가능 → 방어. null이면 1로 취급.
        long current = (count == null) ? 1L : count;

        // 처음 만들어진 카운터(값 1)라면 "다음 자정까지" 살아있도록 수명 설정.
        // 이렇게 하면 24시간 고정이 아니라, 매일 자정에 리셋된다.
        if (current == 1L) {
            redisTemplate.expire(countKey, secondsUntilMidnight());
        }

        // 3회 초과(4번째부터) → 예외. Controller가 이걸 403으로 바꿔줌.
        if (current > TRIAL_LIMIT) {
            throw new TrialLimitExceededException("무료 체험 " + TRIAL_LIMIT + "회를 모두 사용했습니다.");
        }

        // ── 2단계: 리뷰 생성 ──────────────────────────────────
        // 비로그인은 기본 초급 지침으로 리뷰. (나중에 파라미터로 레벨 선택 가능)
        ReviewLevel level = ReviewLevel.BEGINNER;

        // TODO(프롬프트/AI 연동 자리) ────────────────────────────────
        //  아래 buildPrompt(...)로 프롬프트를 만들고, Gemini/Grok에 보내고,
        //  응답(JSON)을 파싱해서 summary + List<ReviewComment>를 채우면 된다.
        //
        //  String prompt = buildPrompt(request.getCode(), request.getLanguage(), level);
        //  String aiRaw  = aiClient.call(prompt);              // 실제 AI 호출 (담당 경계 확인 후)
        //  파싱 결과로 summary, comments 채우기
        //
        //  지금은 AI 연동 전이라 더미 결과로 흐름만 완성해 둔다.
        // ────────────────────────────────────────────────────────────
        String summary = "더미 리뷰 결과입니다. (AI 연동 전 임시 응답 / level=" + level + ")";
        List<ReviewComment> comments = List.of(
                new ReviewComment("BUG", "CRITICAL", "user.ts", 88, "null 체크 누락 — 옵셔널 체이닝 또는 얼리 리턴 권장"),
                new ReviewComment("CONVENTION", "MINOR", "date.ts", 12, "네이밍 컨벤션 — camelCase 통일 권장")
        );

        String reviewId = "G-" + UUID.randomUUID();  // 리뷰마다 고유 이름표
        GuestReviewResponse response = new GuestReviewResponse(reviewId, summary, comments);

        // ── 3단계: Redis에 저장 (다음 자정까지 보관) ──────────
        String reviewKey = "guest:review:" + guestToken + ":" + reviewId;
        try {
            String json = objectMapper.writeValueAsString(response);   // 객체 → JSON 문자열
            redisTemplate.opsForValue().set(reviewKey, json, secondsUntilMidnight());
        } catch (Exception e) {
            throw new RuntimeException("게스트 리뷰 저장 실패", e);
        }

        // ── 4단계: 결과 반환 ─────────────────────────────────
        return response;
    }

    private String buildPrompt(String code, String language, ReviewLevel level) {
        // TODO: 프롬프트 조립해서 return. (지금은 자리만)
        // 예) return switch (level) { case BEGINNER -> "..."; ... };
        return "";
    }

    /** 지금부터 "다음 자정(Asia/Seoul 기준)"까지 남은 시간. 카운터·리뷰 수명에 사용. */
    private Duration secondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now(KST);
        LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(); // 내일 00:00
        return Duration.between(now, nextMidnight);
    }

    /** 3회 초과 시 던지는 예외. Controller가 잡아 403으로 응답. */
    public static class TrialLimitExceededException extends RuntimeException {
        public TrialLimitExceededException(String message) {
            super(message);
        }
    }
}