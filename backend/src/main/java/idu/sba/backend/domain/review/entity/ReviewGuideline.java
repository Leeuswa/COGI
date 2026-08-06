package idu.sba.backend.domain.review.entity;

import idu.sba.backend.domain.user.entity.Level;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 수준별 리뷰 지침 (ADM-004). 레벨당 1행 고정.
// DB에 행이 있으면 그 값이, 없으면 prompts/prompt_level_*.txt 파일이 폴백으로 쓰인다(PromptBuilder).
@Entity
@Table(name = "review_guidelines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewGuideline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(unique = true, nullable = false)
    private Level level;

    @Lob
    private String promptGuideline; // 해당 레벨의 프롬프트 전체(공통규칙 포함) — 파일 내용과 같은 성격

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { this.updatedAt = LocalDateTime.now(); }

    private ReviewGuideline(Level level, String promptGuideline) {
        this.level = level;
        this.promptGuideline = promptGuideline;
    }

    public static ReviewGuideline of(Level level, String promptGuideline) {
        return new ReviewGuideline(level, promptGuideline);
    }

    public void update(String promptGuideline) {
        this.promptGuideline = promptGuideline;
    }
}
