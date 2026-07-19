package idu.sba.backend.domain.payment.repository;

import idu.sba.backend.domain.payment.entity.Subscription;
import idu.sba.backend.domain.payment.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    // 현재 유효한(ACTIVE) 구독 조회
    Optional<Subscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status);

    // 이 사용자의 모든 구독(상태 무관) — 구독 이력 조회에 사용
    List<Subscription> findByUserId(Long userId);

    // 청구대상 + 강등 대상 모두 포함
    List<Subscription> findByStatusAndExpiresAtLessThanEqual(SubscriptionStatus status, LocalDateTime dateTime);
}
