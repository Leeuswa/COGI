package idu.sba.backend.domain.payment.repository;

import idu.sba.backend.domain.payment.entity.SubscriptionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionHistoryRepository extends JpaRepository<SubscriptionHistory , Long> {
    List<SubscriptionHistory> findBySubscriptionId(Long subscriptionId); // 변경 이력 조회용
}
