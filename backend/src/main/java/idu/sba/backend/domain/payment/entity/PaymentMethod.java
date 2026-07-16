package idu.sba.backend.domain.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_methods")
@Getter
@NoArgsConstructor
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId; // 등록한 사용자 (users.id)

    @Column(name = "billing_key", nullable = false, length = 255)
    private String billingKey; // 토스 등 PG 발급 빌링키 (평문 카드정보는 저장 안 함)

    @Column(name = "card_masked_number", length = 30)
    private String cardMaskedNumber; // 화면 표시용 마스킹 번호 (nullable — PG 응답값)

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt; // 등록 일시

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public PaymentMethod(Long userId, String billingKey, String cardMaskedNumber) {
        this.userId = userId;
        this.billingKey = billingKey;
        this.cardMaskedNumber = cardMaskedNumber;
    }

}