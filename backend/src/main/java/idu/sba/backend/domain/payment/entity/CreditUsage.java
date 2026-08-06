package idu.sba.backend.domain.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

//동시 요청(더블클릭, 멀티탭, 웹훅 두 건 동시 도착)이 하루의 첫 소모를 동시에 시도하면 각자 새 행을
//만들려다 부딪히게 해서, 락 없이 조용히 중복 행이 생기고 한도 검사가 갈라지는 것을 막는다
@Entity
@Table(name = "credit_usage", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "usage_date"}))
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

    // AI가 analyzable=false로 응답한 경우 등, 이미 소모한 크레딧을 되돌릴 때 사용
    public void refund(int amount) {
        this.usedCredits = Math.max(0, this.usedCredits - amount);
    }

}
