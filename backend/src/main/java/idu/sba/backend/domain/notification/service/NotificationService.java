package idu.sba.backend.domain.notification.service;

import idu.sba.backend.domain.notification.dto.NotificationResponseDTO;

import java.util.List;

// 인앱 알림(종 아이콘)
public interface NotificationService {

    // 내 알림 목록(최신순)
    List<NotificationResponseDTO> getMyNotifications(Long userId);

    // 알림 삭제(본인 것만)
    void dismiss(Long userId, Long notificationId);

    // 여러 사용자에게 같은 알림을 한 번에 생성(전체 공지 등)
    void broadcast(List<Long> userIds, String icon, String title, String text, String link);

    // noticeId로 공지와 연결해 생성 — 공지 삭제 시 함께 지우기 위함
    void broadcast(List<Long> userIds, String icon, String title, String text, String link, Long noticeId);

    // 특정 공지에서 만든 알림 일괄 삭제
    void deleteByNoticeId(Long noticeId);

    // noticeId 링크가 없는 예전 공지 알림을 '삭제된 알림입니다'로 표시 (제목/내용 매칭)
    void markLegacyNoticeDeleted(String subject, String content);
}
