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

    // 특정 전체 공지에서 만든 알림 일괄 삭제 (공지 삭제 시)
    @Modifying
    @Query("delete from Notification n where n.noticeId = :noticeId")
    int deleteByNoticeId(@Param("noticeId") Long noticeId);

    // noticeId 링크가 없는 예전 공지 알림 — 지울 식별자가 없어 제목/내용으로 찾아 '삭제된 알림입니다'로 표시.
    // ponytail: 제목+내용 완전일치 매칭. 같은 문구 공지가 여럿이면 함께 바뀔 수 있음(예전 데이터 정리용, 허용).
    @Modifying
    @Query("update Notification n set n.title = '삭제된 알림입니다', n.text = null " +
           "where n.noticeId is null and n.title = :title and n.text = :text")
    int markLegacyNoticeDeleted(@Param("title") String title, @Param("text") String text);
}
