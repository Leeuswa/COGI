package idu.sba.backend.domain.payment.dto;

import java.time.LocalDateTime;

public record SubscriptionHistoryResponseDTO (
        Long previousPlanId,     // 변경 전 요금제 ID
        Long newPlanId,           // 변경 후 요금제 ID
        String changeType,        // 변경 종류 (UPGRADE/RENEWAL/CANCEL)
        Integer proratedAmount,   // 일할계산 표시금액 (테스트용 계산값, 없으면 null)
        LocalDateTime changedAt   // 이 변경이 일어난 시각
){
}
