package idu.sba.backend.domain.payment.service;

import idu.sba.backend.domain.payment.dto.*;
import idu.sba.backend.domain.payment.entity.*;
import idu.sba.backend.domain.payment.repository.PaymentMethodRepository;
import idu.sba.backend.domain.payment.repository.PlanRepository;
import idu.sba.backend.domain.payment.repository.SubscriptionHistoryRepository;
import idu.sba.backend.domain.payment.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final PlanRepository planRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionHistoryRepository subscriptionHistoryRepository;

    // FREE 플랜 판별 기준
    private static final String FREE_PLAN_NAME = "FREE";

    //결제수단 등록
    @Override
    public Long registerPaymentMethod(Long userId, PaymentMethodRequestDTO dto) {
        // 더미데이터 PG 연동 예정
        PaymentMethod pm = new PaymentMethod(userId, "TEST_BILLING_KEY_...", "****-****-****-1234");
        return paymentMethodRepository.save(pm).getId();
    }


    //요금제 목록
    @Override
    public List<PlanResponseDTO> getPlans() {
        return planRepository.findAll().stream()
                .map(p -> new PlanResponseDTO(
                        p.getId(), p.getName(), p.getDailyCreditLimit(),
                        p.getAllowedModels(), p.getPrice()))
                .toList();
    }

    //내 플랜 확인 (구독시에만)
    @Override
    public MyPlanResponseDTO getMyPlan(Long userId) {
        Subscription sub = subscriptionRepository
                .findByUserIdAndStatus(userId , SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("구독중인 플랜이 없습니다."));
        Plan plan = planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new IllegalStateException("구독중인 플랜을 찾을 수 없습니다."));
        return new MyPlanResponseDTO(plan.getId(), plan.getName() , sub.getStartedAt());
    }


    @Override
    @Transactional
    public Long createSubscription(Long userId, SubscriptionCreateDTO dto) {
        // TODO: dto.agreeTerms() 로 결제/구독 약관 동의 여부 검증 (Terms 도메인 연계)

        Plan newPlan = planRepository.findById(dto.planId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플랜입니다."));
        Plan freePlan = planRepository.findByName(FREE_PLAN_NAME)
                .orElseThrow(() -> new IllegalStateException("FREE 플랜 시드 데이터가 없습니다."));

        // 1) 구독 생성
        Subscription sub = new Subscription();
        sub.start(userId, newPlan.getId(), dto.paymentMethodId()); // status=ACTIVE, startedAt=now
        Subscription saved = subscriptionRepository.save(sub);

        // 2) history 기록 (previous=FREE, UPGRADE, prorated=새 플랜 가격 그대로)
        SubscriptionHistory history = new SubscriptionHistory();
        history.record(
                saved.getId(),
                freePlan.getId(),        // previous_plan_id = FREE
                newPlan.getId(),         // new_plan_id
                ChangeType.UPGRADE,
                newPlan.getPrice()       // ★ 최초 구독은 일할계산 X, 가격 그대로
        );
        subscriptionHistoryRepository.save(history);

        // 3) users.plan_id 캐시 갱신
        // TODO: User 도메인 연계 — userRepository 로 users.plan_id = newPlan.getId() 갱신

        return saved.getId();
    }

    // 구독 시작 ( 최초구독시 가격 그대로 )
    @Override
    @Transactional
    public SubscriptionHistoryResponseDTO changeSubscription(Long subId, SubscriptionChangeDTO dto) {
        Subscription sub = subscriptionRepository.findById(subId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        Long previousPlanId = sub.getPlanId();
        Plan oldPlan = planRepository.findById(previousPlanId)
                .orElseThrow(() -> new IllegalStateException("기존 플랜을 찾을 수 없습니다."));
        Plan newPlan = planRepository.findById(dto.newPlanId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플랜입니다."));

        //일할계산 (남은일수 기준 차액)
        int prorated = calculateProratedAmount(
                oldPlan.getPrice(), newPlan.getPrice(), LocalDate.now());

        // 업그레이드/다운그레이드 판별 (가격 비교)
        ChangeType changeType = newPlan.getPrice() >= oldPlan.getPrice()
                ? ChangeType.UPGRADE : ChangeType.DOWNGRADE;

        // 기존 구독 행 유지 + plan_id만 전환
        sub.changePlan(newPlan.getId());

        //history 기록
        SubscriptionHistory history = new SubscriptionHistory();
        history.record(subId, previousPlanId, newPlan.getId(), changeType, prorated);
        SubscriptionHistory savedHistory = subscriptionHistoryRepository.save(history);

        return new SubscriptionHistoryResponseDTO(
                savedHistory.getPreviousPlanId(), savedHistory.getNewPlanId(),
                savedHistory.getChangeType().name(), savedHistory.getProratedAmount(),
                savedHistory.getChangedAt());
    }

    // API-062
    @Override
    @Transactional
    public void cancelSubscription(Long subId) {
        Subscription sub = subscriptionRepository.findById(subId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));
        sub.cancel(); // status=CANCELLED, cancelledAt=now
    }

    // 일할계산: (새가격 - 기존가격) × 남은일수 ÷ 그 달 총일수
    private int calculateProratedAmount(int oldPrice, int newPrice, LocalDate changeDate) {
        int totalDays = changeDate.lengthOfMonth();
        int remainingDays = totalDays - changeDate.getDayOfMonth();
        int diff = newPrice - oldPrice;
        return diff * remainingDays / totalDays; // 곱셈 먼저, 나눗셈 나중 (정수 나눗셈 주의)
    }
}
