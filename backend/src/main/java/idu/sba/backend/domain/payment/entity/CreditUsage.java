package idu.sba.backend.domain.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "credit_usage")
@Getter
@NoArgsConstructor
public class CreditUsage { // 일일 리뷰 요청 크레딧 소진량(플랜별 daily_credit_limit 대비)

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "used_credits", nullable = false)
    private int usedCredits;

    @Column(name = "daily_limit", nullable = false)
    private int dailyLimit;

    public void start(Long userId, LocalDate usageDate, int dailyLimit) {
        this.userId = userId;
        this.usageDate = usageDate;
        this.dailyLimit = dailyLimit;
        this.usedCredits = 0;
    }

    public boolean hasRemaining(int amount) {
        return usedCredits + amount <= dailyLimit;
    }

    public void consume(int amount) {
        this.usedCredits += amount;
    }

}
