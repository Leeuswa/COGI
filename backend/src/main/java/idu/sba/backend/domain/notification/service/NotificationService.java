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

    // 오늘 같은 link로 만든 알림이 없을 때만 생성. 만들었으면 true.
    // 학습 계획 알림은 접속할 때마다 만들려 하므로 중복 방지가 서비스 쪽에 있어야 한다
    boolean createOncePerDay(Long userId, String icon, String title, String text, String link);
}
