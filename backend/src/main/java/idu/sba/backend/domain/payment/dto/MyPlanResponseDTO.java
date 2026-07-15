package idu.sba.backend.domain.payment.dto;


import java.time.LocalDateTime;

// GET /api/users/me/plan 응답용
public record MyPlanResponseDTO(
        Long planId,             // 현재 구독 중인 요금제 ID
        String planName,         // 현재 구독 중인 요금제 이름 (PRO , MAX )
        LocalDateTime startedAt  // 이 구독을 시작한 시각
) {}
