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
}
