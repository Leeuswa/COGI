package idu.sba.backend.domain.payment.repository;

import idu.sba.backend.domain.payment.entity.Subscription;
import idu.sba.backend.domain.payment.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    // 현재 유효한(ACTIVE) 구독 조회
    Optional<Subscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status);
}
