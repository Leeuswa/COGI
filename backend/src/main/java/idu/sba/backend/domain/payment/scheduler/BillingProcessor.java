package idu.sba.backend.domain.payment.scheduler;

import idu.sba.backend.domain.payment.client.TossPaymentClient;
import idu.sba.backend.domain.payment.entity.*;
import idu.sba.backend.domain.payment.repository.PaymentMethodRepository;
import idu.sba.backend.domain.payment.repository.PlanRepository;
import idu.sba.backend.domain.payment.repository.SubscriptionHistoryRepository;
import idu.sba.backend.domain.payment.repository.SubscriptionRepository;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.mail.HtmlMailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// @Transactional은 스프링 프록시가 걸어줘서, 같은 클래스 self-invocation이면 무시됨.
@Service
@RequiredArgsConstructor
@Slf4j
public class BillingProcessor {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final SubscriptionHistoryRepository historyRepository;
    private final TossPaymentClient tossPaymentClient;
    private final HtmlMailSender htmlMailSender;

    @Transactional
    public void processOne(Long subId, Long freeId) {
        //트랜잭션 안에서 다시 조회 → managed 상태 → expire()/extend()/updatePlanId() 더티체킹 반영
        Subscription sub = subscriptionRepository.findById(subId).orElseThrow();

        // 해지 예약 + 기간 만료 → FREE 강등 (CANCEL 이력 남김)
        if (sub.getCancelledAt() != null) {
            downgradeToFree(sub, freeId);
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
        // 정기결제 = RENEWAL (기존엔 UPGRADE로 잘못 기록)
        saveHistory(sub.getId(), sub.getPlanId(), sub.getPlanId(), ChangeType.RENEWAL, plan.getPrice());
        // 결제 실패 시 confirmBilling에서 예외 → 트랜잭션 롤백 + 스케줄러가 handlePaymentFailure 호출

        // 정기결제 청구 메일 (실패해도 결제/연장은 유지)
        try {
            userRepository.findById(sub.getUserId()).ifPresent(u ->
                    sendBillingMail(u.getEmail(), plan.getName(), plan.getPrice(), pm.getCardMaskedNumber()));
        } catch (Exception e) {
            log.warn("정기결제 청구 메일 발송 실패 subId={}: {}", subId, e.getMessage());
        }
    }

    // 결제 실패 시: FREE 강등. 스케줄러 catch에서 별도 트랜잭션으로 호출
    // (processOne 롤백과 분리해야 강등 기록이 남음)
    @Transactional
    public void handlePaymentFailure(Long subId, Long freeId) {
        Subscription sub = subscriptionRepository.findById(subId).orElseThrow();
        downgradeToFree(sub, freeId);
    }

    // FREE 강등 공통 처리 (해지 만료 / 결제 실패 둘 다) + CANCEL 이력
    private void downgradeToFree(Subscription sub, Long freeId) {
        Long previousPlanId = sub.getPlanId();
        sub.expire();
        userRepository.findById(sub.getUserId())
                .ifPresent(u -> u.updatePlanId(freeId));
        saveHistory(sub.getId(), previousPlanId, freeId, ChangeType.CANCEL, 0);
    }

    private void saveHistory(Long subId, Long prevPlanId, Long newPlanId, ChangeType type, Integer amount) {
        SubscriptionHistory h = new SubscriptionHistory();
        h.record(subId, prevPlanId, newPlanId, type, amount);
        historyRepository.save(h);
    }

    // 결제 청구 메일 본문
    private void sendBillingMail(String to, String planName, int amount, String cardMasked) {
        String inner = """
            <p style="margin:0 0 8px;color:#1b2a4a;font-size:17px;font-weight:bold;">결제 완료</p>
            <p style="margin:0 0 20px;color:#40507a;font-size:13px;">정기 구독 결제가 정상 처리되었습니다.</p>
            <div style="background:#fdfcf7;border:3px solid #1b2a4a;padding:16px;color:#1b2a4a;font-size:14px;">
              <p style="margin:0 0 6px;">플랜: <b>%s</b></p>
              <p style="margin:0 0 6px;">금액: <b>%,d원</b></p>
              <p style="margin:0;">결제수단: <b>%s</b></p>
            </div>
            """.formatted(planName, amount, cardMasked);
        htmlMailSender.send(to, "[COGI] 정기결제가 완료되었습니다", inner);
    }

}
