package idu.sba.backend.domain.payment.service;


import idu.sba.backend.domain.payment.dto.*;
import idu.sba.backend.domain.payment.entity.Plan;

import java.util.List;

// 구독/결제 도메인 로직
public interface SubscriptionService {


    // 전체 요금제 목록 조회
    List<PlanResponseDTO> getPlans();

    // 내 현재 플랜 조회 (ACTIVE 구독 기준)
    MyPlanResponseDTO getMyPlan(Long userId);

    // 현재 적용 중인 플랜 엔티티 조회 (ACTIVE 구독 없으면 FREE로 간주) — 크레딧 한도 등 내부 로직용
    Plan getCurrentPlanEntity(Long userId);

    // 결제수단 등록 (카드정보로 빌링키 발급 -> payment_methods로 저장)
    Long registerPaymentMethod(Long userId , PaymentMethodRequestDTO dto);

    //구독 시작
    Long createSubscription(Long userId, SubscriptionCreateDTO dto);

    //플랜 변경 (본인 구독만)
    SubscriptionHistoryResponseDTO changeSubscription(Long userId, Long subId, SubscriptionChangeDTO dto);

    //구독 해지 (본인 구독만)
    void cancelSubscription(Long userId, Long subId);

    //해지 예약 취소 (본인 구독만) — cancelledAt 초기화
    void resumeSubscription(Long userId, Long subId);

    //내 구독 변경 이력 (최신순, 플랜은 이름으로)
    List<SubHistoryItemDTO> getSubHistory(Long userId);
}
