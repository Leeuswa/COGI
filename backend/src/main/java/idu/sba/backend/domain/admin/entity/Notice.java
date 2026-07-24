package idu.sba.backend.domain.admin.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 관리자 전체 공지 발송 이력
@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long senderId; // 발송한 관리자 userId

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String content;

    private int recipientCount; // 발송 대상 수(발송 시작 시점)
    private Integer successCount; // 발송 완료 후 채워짐(진행 중엔 null)
    private Integer failCount;

    @Enumerated(EnumType.STRING)
    private NoticeStatus status; // SENDING → 발송 끝나면 SENT

    @Column(updatable = false)
    private LocalDateTime createdAt; // 발송 시작
    private LocalDateTime sentAt;    // 발송 완료

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    private Notice(Long senderId, String subject, String content, int recipientCount) {
        this.senderId = senderId;
        this.subject = subject;
        this.content = content;
        this.recipientCount = recipientCount;
        this.status = NoticeStatus.SENDING;
    }

    public static Notice create(Long senderId, String subject, String content, int recipientCount) {
        return new Notice(senderId, subject, content, recipientCount);
    }

    // 비동기 발송 완료 후 결과 기록
    public void markSent(int successCount, int failCount) {
        this.successCount = successCount;
        this.failCount = failCount;
        this.status = NoticeStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }
}
