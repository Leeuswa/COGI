package idu.sba.backend.domain.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor
public class Subscription { // 구독 현재 상태 엔티티

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId; // 구독자 (users.id)

    @Column(name = "plan_id", nullable = false)
    private Long planId; // 현재 플랜 (변경 시 이 값만 갱신 + users.plan_id 캐시도 함께 갱신)

    @Column(name = "payment_method_id", nullable = false)
    private Long paymentMethodId; // 결제수단 (payment_methods.id)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status; //  구독 상태 ACTIVE / CANCELLED (DB 기본값 ACTIVE)

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt; // 구독 시작 일시

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt; // 해지 일시 (해지 전 NULL) 빌링키를 넣기 때문에 필요

    // 상태 변경은 setter 대신 의미 있는 메서드로
    public void changePlan(Long newPlanId) {
        this.planId = newPlanId;
    }

    public void cancel() {
        this.status = SubscriptionStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public void start(Long userId, Long planId, Long paymentMethodId) {
        this.userId = userId;
        this.planId = planId;
        this.paymentMethodId = paymentMethodId;
        this.status = SubscriptionStatus.ACTIVE;
        this.startedAt = LocalDateTime.now();
    }


    @PrePersist
    void prePersist() {
        if (startedAt == null) startedAt = LocalDateTime.now();
        if (status == null) status = SubscriptionStatus.ACTIVE; // NOT NULL 방어
    }
}