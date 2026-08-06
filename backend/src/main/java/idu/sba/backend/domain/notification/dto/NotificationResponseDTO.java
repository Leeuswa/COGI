package idu.sba.backend.domain.notification.dto;

import idu.sba.backend.domain.notification.entity.Notification;

import java.time.LocalDateTime;

// 프론트 종 알림 한 행 — { id, icon, title, text, link } 형태를 그대로 맞춘다.
public record NotificationResponseDTO(
        Long id,
        String icon,
        String title,
        String text,
        String link,
        LocalDateTime createdAt
) {
    public static NotificationResponseDTO of(Notification n) {
        return new NotificationResponseDTO(n.getId(), n.getIcon(), n.getTitle(), n.getText(), n.getLink(), n.getCreatedAt());
    }
}
