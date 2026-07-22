package idu.sba.backend.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 인앱 알림(종 아이콘) — 초대/공지 등 사용자별로 쌓이는 이벤트 한 건.
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notifications_user", columnList = "userId"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId; // 받는 사람

    private String icon;  // 종 목록 앞 이모지
    private String title; // 굵은 제목

    @Column(columnDefinition = "TEXT")
    private String text;  // 본문

    private String link;  // 클릭 시 이동 경로 (예: /app)

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    private Notification(Long userId, String icon, String title, String text, String link) {
        this.userId = userId;
        this.icon = icon;
        this.title = title;
        this.text = text;
        this.link = link;
    }

    public static Notification of(Long userId, String icon, String title, String text, String link) {
        return new Notification(userId, icon, title, text, link);
    }
}
