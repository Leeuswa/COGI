package idu.sba.backend.domain.payment.dto;

import java.time.LocalDateTime;

// GET /api/users/me/subscription-history 응답용 (프론트 이력 테이블 형태에 맞춤 — 플랜은 이름으로).
public record SubHistoryItemDTO(
        Long id,
        String previousPlan,    // 변경 전 플랜 이름 (첫 구독이면 "FREE")
        String newPlan,         // 변경 후 플랜 이름
        String changeType,      // UPGRADE / RENEWAL / CANCEL
        Integer proratedAmount, // 일할계산 표시금액 (없으면 null)
        LocalDateTime changedAt
) {}
