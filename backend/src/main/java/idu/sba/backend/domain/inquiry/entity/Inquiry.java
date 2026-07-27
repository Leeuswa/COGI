package idu.sba.backend.domain.inquiry.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 사용자 1:1 문의 — 사용자가 질문을 남기면 관리자가 답변한다. answer==null 이면 미답변.
@Entity
@Table(name = "inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId; // 질문한 사용자

    // 관리자 목록에서 누가 물었는지 바로 보이도록 작성 시점 스냅샷 저장(닉네임 변경돼도 당시 값 유지)
    private String askerEmail;
    private String askerNickname;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String answer; // null이면 미답변

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime answeredAt;

    @PrePersist void onCreate() { this.createdAt = LocalDateTime.now(); }

    private Inquiry(Long userId, String askerEmail, String askerNickname, String title, String content) {
        this.userId = userId;
        this.askerEmail = askerEmail;
        this.askerNickname = askerNickname;
        this.title = title;
        this.content = content;
    }

    public static Inquiry create(Long userId, String askerEmail, String askerNickname, String title, String content) {
        return new Inquiry(userId, askerEmail, askerNickname, title, content);
    }

    public void answer(String answer) {
        this.answer = answer;
        this.answeredAt = LocalDateTime.now();
    }

    public boolean isAnswered() {
        return answer != null && !answer.isBlank();
    }
}
