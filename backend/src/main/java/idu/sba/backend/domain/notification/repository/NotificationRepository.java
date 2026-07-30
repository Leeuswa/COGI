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

    // 같은 알림을 오늘 이미 만들었는지 — 학습 계획 알림은 접속할 때마다 만들어서 중복 방지가 필요하다.
    // link에 카드id와 단계가 들어 있어 단계별로 따로 판정된다(다른 종류 알림은 영향 없음)
    boolean existsByUserIdAndLinkAndCreatedAtAfter(Long userId, String link, LocalDateTime after);

    // 보관기간 지난 알림 일괄 삭제
    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
