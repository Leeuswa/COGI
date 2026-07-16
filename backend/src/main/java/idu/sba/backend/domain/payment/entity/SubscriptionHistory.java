package idu.sba.backend.domain.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_history")
@Getter
@NoArgsConstructor
public class SubscriptionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId; // 대상 구독 건 (subscriptions.id)

    @Column(name = "previous_plan_id")
    private Long previousPlanId; // 변경 전 플랜 (변경 전이 없다면 null)

    @Column(name = "new_plan_id", nullable = false)
    private Long newPlanId; // 변경 후 플랜 (plans_id)

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private ChangeType changeType; // UPGRADE / DOWNGRADE / CANCEL

    @Column(name = "prorated_amount")
    private Integer proratedAmount; // 계산 표시금액(테스트용)

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt; // 변경 일시

    @PrePersist
    void prePersist() {
        if (changedAt == null) changedAt = LocalDateTime.now();
    }


    public void record(Long subscriptionId, Long previousPlanId, Long newPlanId,
                       ChangeType changeType, Integer proratedAmount) {
        this.subscriptionId = subscriptionId;
        this.previousPlanId = previousPlanId;
        this.newPlanId = newPlanId;
        this.changeType = changeType;
        this.proratedAmount = proratedAmount;
    }
}