package idu.sba.backend.domain.payment.dto;


import java.time.LocalDate;
import java.time.LocalDateTime;

// GET /api/users/me/plan 응답용
public record MyPlanResponseDTO(
        Long subscriptionId,      // 현재 구독 행의 ID — 해지/전환에 사용. 구독 없으면(FREE) null
        Long planId,              // 현재 구독 중인 요금제 ID
        String planName,          // 현재 구독 중인 요금제 이름 (PRO , MAX )
        LocalDateTime startedAt,  // 이 구독을 시작한 시각
        LocalDate expiresAt,      // 만료일(다음 결제일). 해지 예약 시 이 날 FREE로 강등. FREE면 null
        LocalDateTime cancelledAt // 해지 예약 시각. 예약 안 됐으면 null → 이 값이 있으면 "해지 예약됨"
) {}
