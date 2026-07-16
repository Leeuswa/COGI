package idu.sba.backend.domain.payment.service;

import idu.sba.backend.domain.payment.client.TossPaymentClient;
import idu.sba.backend.domain.payment.dto.*;
import idu.sba.backend.domain.payment.entity.*;
import idu.sba.backend.domain.payment.repository.PaymentMethodRepository;
import idu.sba.backend.domain.payment.repository.PlanRepository;
import idu.sba.backend.domain.payment.repository.SubscriptionHistoryRepository;
import idu.sba.backend.domain.payment.repository.SubscriptionRepository;
import idu.sba.backend.domain.terms.entity.Term;
import idu.sba.backend.domain.terms.entity.TermType;
import idu.sba.backend.domain.terms.entity.UserAgreement;
import idu.sba.backend.domain.terms.repository.TermRepository;
import idu.sba.backend.domain.terms.repository.UserAgreementRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final PlanRepository planRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionHistoryRepository subscriptionHistoryRepository;
    private final UserRepository userRepository;
    private final TossPaymentClient tossPaymentClient;
    private final TermRepository termRepository;
    private final UserAgreementRepository userAgreementRepository;



    //결제수단 등록
    @Override
    public Long registerPaymentMethod(Long userId, PaymentMethodRequestDTO dto) {
        // customerKey 새로 생성 (UUID 형태) 구매자를 식별하기 위함
        String customerKey = UUID.randomUUID().toString();

        var response = tossPaymentClient.issueBillingKey(customerKey, dto); // 실제 토스 호출

        PaymentMethod pm = new PaymentMethod(
                userId, customerKey, response.billingKey(), response.card().number());
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


        if (dto.agreeTerms() == null || dto.agreeTerms().isEmpty())
            throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);

        // #2 중복 ACTIVE 구독 방지 — 이미 구독 중이면 거절
        subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .ifPresent(s -> { throw new BusinessException(ErrorCode.ALREADY_SUBSCRIBED); });

        // 약관 검증: (1) 존재하는 약관 id인지 (2) 결제 필수 약관에 동의했는지
        List<Term> allTerms = termRepository.findAll();
        Set<Long> validTermIds = allTerms.stream().map(Term::getId).collect(Collectors.toSet());
        if (!validTermIds.containsAll(dto.agreeTerms())) {          // #9 존재하지 않는 약관 거절
            throw new BusinessException(ErrorCode.INVALID_TERMS);
        }
        for (Term t : allTerms) {
            if (t.getType() == TermType.PAYMENT && !dto.agreeTerms().contains(t.getId())) {
                throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
            }
        }

        //동의 약관 DB에 저장
        dto.agreeTerms().forEach(termId ->
                userAgreementRepository.save(UserAgreement.of(userId, termId)));

        // 새로 구독할 플랜 조회
        Plan newPlan = planRepository.findById(dto.planId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));

        // #3 결제수단 존재 + 소유 검증 (남의 결제수단으로 구독 못 하게)
        PaymentMethod pm = paymentMethodRepository.findById(dto.paymentMethodId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_METHOD_NOT_FOUND));
        if (!pm.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.PAYMENT_METHOD_FORBIDDEN);

        // 구독 생성 (status=ACTIVE, startedAt=now) 후 저장
        Subscription sub = new Subscription();
        sub.start(userId, newPlan.getId(), pm.getId()); // status=ACTIVE, startedAt=now
        Subscription saved = subscriptionRepository.save(sub);

        // #5 최초 결제 — 빌링키로 즉시 1회 청구 (실패 시 @Transactional 롤백)
        String orderId = "SUB-" + saved.getId() + "-" + UUID.randomUUID();
        tossPaymentClient.confirmBilling(
                pm.getBillingKey(), pm.getCustomerKey(), newPlan.getPrice(),
                orderId, newPlan.getName() + " 구독 결제");

        //history 기록 (최초구독 previous=null, UPGRADE, prorated=가격 그대로)
        SubscriptionHistory history = new SubscriptionHistory();
        history.record(
                saved.getId(),
                null,
                newPlan.getId(),         // new_plan_id
                ChangeType.UPGRADE,
                newPlan.getPrice()       // 최초 구독은 일할계산 X 가격은 그대로
        );
        subscriptionHistoryRepository.save(history);

        //user 테이블 plan_id 캐시 갱신
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.updatePlanId(newPlan.getId());

        return saved.getId();
    }

    // 플랜 변경
    @Override
    @Transactional
    public SubscriptionHistoryResponseDTO changeSubscription(Long userId, Long subId, SubscriptionChangeDTO dto) {
        Subscription sub = subscriptionRepository.findById(subId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));

        // #1 소유자 검증 — 남의 구독은 변경 불가
        if (!sub.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.SUBSCRIPTION_FORBIDDEN);

        Long previousPlanId = sub.getPlanId();
        Plan oldPlan = planRepository.findById(previousPlanId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        Plan newPlan = planRepository.findById(dto.newPlanId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));

        //일할계산 (남은일수 기준 차액)
        int prorated = calculateProratedAmount(
                oldPlan.getPrice(), newPlan.getPrice(), LocalDate.now());

        // 기존 구독 행 유지 + plan_id만 전환
        sub.changePlan(newPlan.getId());

        //history 기록
        SubscriptionHistory history = new SubscriptionHistory();
        history.record(subId, previousPlanId, newPlan.getId(), ChangeType.UPGRADE, prorated);
        SubscriptionHistory savedHistory = subscriptionHistoryRepository.save(history);

        User user = userRepository.findById(sub.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.updatePlanId(newPlan.getId());


        return new SubscriptionHistoryResponseDTO(
                savedHistory.getPreviousPlanId(), savedHistory.getNewPlanId(),
                savedHistory.getChangeType().name(), savedHistory.getProratedAmount(),
                savedHistory.getChangedAt());
    }


    @Override
    @Transactional
    public void cancelSubscription(Long userId, Long subId) {
        Subscription sub = subscriptionRepository.findById(subId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));

        // #1 소유자 검증 — 남의 구독은 해지 불가
        if (!sub.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.SUBSCRIPTION_FORBIDDEN);

        // 해지 예약 — cancelledAt만 기록, status는 ACTIVE 유지. 만료일에 스케줄러가 FREE 강등
        sub.reserveCancel();
    }

    // 일할계산: (새가격 - 기존가격) × 남은일수 ÷ 그 달 총일수
    private int calculateProratedAmount(int oldPrice, int newPrice, LocalDate changeDate) {
        int totalDays = changeDate.lengthOfMonth();
        int remainingDays = totalDays - changeDate.getDayOfMonth();
        int diff = newPrice - oldPrice;
        return diff * remainingDays / totalDays;
    }
}
