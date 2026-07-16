package idu.sba.backend.domain.payment.service;


import idu.sba.backend.domain.payment.dto.*;

import java.util.List;

// 구독/결제 도메인 로직
public interface SubscriptionService {


    // 전체 요금제 목록 조회
    List<PlanResponseDTO> getPlans();

    // 내 현재 플랜 조회 (ACTIVE 구독 기준)
    MyPlanResponseDTO getMyPlan(Long userId);

    // 결제수단 등록 (카드정보로 빌링키 발급 -> payment_methods로 저장)
    Long registerPaymentMethod(Long userId , PaymentMethodRequestDTO dto);

    //구독 시작
    Long createSubscription(Long userId, SubscriptionCreateDTO dto);

    //플랜 변경 (본인 구독만)
    SubscriptionHistoryResponseDTO changeSubscription(Long userId, Long subId, SubscriptionChangeDTO dto);

    //구독 해지 (본인 구독만)
    void cancelSubscription(Long userId, Long subId);
}
