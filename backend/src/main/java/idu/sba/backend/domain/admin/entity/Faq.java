package idu.sba.backend.domain.admin.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 관리자가 등록하는 자주 묻는 질문
@Entity
@Table(name = "faqs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Faq {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    private String category; // 분류(계정/결제/리뷰 등), null 허용

    private boolean visible;  // false면 사용자 화면(FAQ 페이지/푸터)에서 숨김

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() { this.createdAt = this.updatedAt = LocalDateTime.now(); }
    @PreUpdate  void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    private Faq(String question, String answer, String category, boolean visible) {
        this.question = question; this.answer = answer; this.category = category;
        this.visible = visible;
    }

    public static Faq create(String question, String answer, String category, boolean visible) {
        return new Faq(question, answer, category, visible);
    }

    public void update(String question, String answer, String category, boolean visible) {
        this.question = question; this.answer = answer; this.category = category;
        this.visible = visible;
    }
}
