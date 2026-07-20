package idu.sba.backend.domain.payment.repository;

import idu.sba.backend.domain.payment.entity.SubscriptionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionHistoryRepository extends JpaRepository<SubscriptionHistory , Long> {
    List<SubscriptionHistory> findBySubscriptionId(Long subscriptionId); // 변경 이력 조회용

    // 여러 구독 건의 이력을 최신순으로 — 사용자 전체 이력 조회용
    List<SubscriptionHistory> findBySubscriptionIdInOrderByChangedAtDesc(List<Long> subscriptionIds);
}
