package idu.sba.backend.domain.payment.scheduler;

import idu.sba.backend.domain.payment.client.TossPaymentClient;
import idu.sba.backend.domain.payment.entity.*;
import idu.sba.backend.domain.payment.repository.PaymentMethodRepository;
import idu.sba.backend.domain.payment.repository.PlanRepository;
import idu.sba.backend.domain.payment.repository.SubscriptionHistoryRepository;
import idu.sba.backend.domain.payment.repository.SubscriptionRepository;
import idu.sba.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// 구독 1건 처리 (정기결제 or 강등). 스케줄러와 분리한 이유:
// @Transactional은 스프링 프록시가 걸어줘서, 같은 클래스 self-invocation이면 무시됨.
// 별도 빈으로 주입받아 호출해야 트랜잭션이 실제로 적용된다.
@Service
@RequiredArgsConstructor
public class BillingProcessor {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final SubscriptionHistoryRepository historyRepository;
    private final TossPaymentClient tossPaymentClient;

    @Transactional
    public void processOne(Long subId, Long freeId) {
        //트랜잭션 안에서 다시 조회 → managed 상태 → expire()/extend()/updatePlanId() 더티체킹 반영
        Subscription sub = subscriptionRepository.findById(subId).orElseThrow();

        // 해지 예약 + 기간 만료 → FREE 강등
        if (sub.getCancelledAt() != null) {
            sub.expire();
            userRepository.findById(sub.getUserId())
                    .ifPresent(u -> u.updatePlanId(freeId));
            return;
        }

        // 정기결제
        Plan plan = planRepository.findById(sub.getPlanId()).orElseThrow();
        PaymentMethod pm = paymentMethodRepository.findById(sub.getPaymentMethodId()).orElseThrow();
        String orderId = "SUB-" + sub.getId() + "-" + UUID.randomUUID();

        tossPaymentClient.confirmBilling(
                pm.getBillingKey(), pm.getCustomerKey(), plan.getPrice(),
                orderId, plan.getName() + " 정기결제");

        sub.extend(); // 다음 달로 (더티체킹으로 저장)
        SubscriptionHistory h = new SubscriptionHistory();
        h.record(sub.getId(), sub.getPlanId(), sub.getPlanId(), ChangeType.UPGRADE, plan.getPrice());
        historyRepository.save(h);
        // 결제 실패 시 confirmBilling에서 예외 → 트랜잭션 롤백 + 스케줄러 catch로 빠짐
    }
}
