package idu.sba.backend.domain.notification.repository;

import idu.sba.backend.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 종 아이콘 목록 — 내 알림만, 최신순
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 보관기간 지난 알림 일괄 삭제
    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
