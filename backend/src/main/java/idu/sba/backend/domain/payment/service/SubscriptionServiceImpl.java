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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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


        if(dto.agreeTerms() == null || dto.agreeTerms().isEmpty())
            throw new IllegalArgumentException("결제 약관 동의가 필요합니다");


        // 결제 필수 약관(PAYMENT) 동의 및 검증
        List<Term> paymentTerms = termRepository.findAll().stream()
                .filter(t -> t.getType() == TermType.PAYMENT)
                .toList();

        // 결제 약관 미동의시 예외처리
        for (Term t : paymentTerms) {
            if (!dto.agreeTerms().contains(t.getId())) {
                throw new IllegalArgumentException("결제 약관 동의가 필요합니다.");
            }
        }

        //동의 약관 DB에 저장
        dto.agreeTerms().forEach(termId ->
                userAgreementRepository.save(UserAgreement.of(userId, termId)));


        // 새로 구독할 플랜조회 ( + 예외처리 구문)
        Plan newPlan = planRepository.findById(dto.planId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플랜입니다."));

        // 구독 생성 (status=ACTIVE, startedAt=now) 후 저장
        Subscription sub = new Subscription();
        sub.start(userId, newPlan.getId(), dto.paymentMethodId()); // status=ACTIVE, startedAt=now
        Subscription saved = subscriptionRepository.save(sub);

        //history 기록 (previous = 전 플랜 또는 null , UPGRADE, prorated=새 플랜 가격 그대로)
        SubscriptionHistory history = new SubscriptionHistory();
        history.record(
                saved.getId(),
                null,
                newPlan.getId(),         // new_plan_id
                ChangeType.UPGRADE,
                newPlan.getPrice()       // 최초 구독은 일할계산 X 가격은 그대로
        );
        subscriptionHistoryRepository.save(history);

        //user 테이블 plan_id 갱신 ( + 예외처리 구문)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        user.updatePlanId(newPlan.getId());

        return saved.getId();
    }

    // 플랜 변경
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

        User user = userRepository.findById(sub.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        user.updatePlanId(newPlan.getId());


        return new SubscriptionHistoryResponseDTO(
                savedHistory.getPreviousPlanId(), savedHistory.getNewPlanId(),
                savedHistory.getChangeType().name(), savedHistory.getProratedAmount(),
                savedHistory.getChangedAt());
    }


    @Override
    @Transactional
    public void cancelSubscription(Long subId) {
        Subscription sub = subscriptionRepository.findById(subId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));
        sub.reserveCancel(); // status=CANCELLED, cancelledAt=now
    }

    // 일할계산: (새가격 - 기존가격) × 남은일수 ÷ 그 달 총일수
    private int calculateProratedAmount(int oldPrice, int newPrice, LocalDate changeDate) {
        int totalDays = changeDate.lengthOfMonth();
        int remainingDays = totalDays - changeDate.getDayOfMonth();
        int diff = newPrice - oldPrice;
        return diff * remainingDays / totalDays;
    }
}
