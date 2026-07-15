package idu.sba.backend.domain.payment.dto;


import java.util.List;

// POST /api/subscription 요청용 - 구독 시작할 때 씀
public record SubscriptionCreateDTO(
        Long planId,                // 구독하려는 요금제 Id
        Long paymentMethodId,       // 어떤 결제수단으로 결제하기
        List<Long> agrreTerms       // 동의한 약관 ID 목록
) {}
