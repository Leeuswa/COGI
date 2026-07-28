package idu.sba.backend.domain.learning.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// AI 스킬 추천(API-049) 호출 이력 한 줄. 크레딧 쓰는 호출이라 언제 뭘 물어봤고 뭐라 답했는지 그대로 남긴다.
@Entity
@Table(name = "ai_skill_recommendations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSkillRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Lob
    @Column(nullable = false)
    private String inputText; // 사용자가 적은 약점/요구사항 원문

    @Lob
    @Column(nullable = false)
    private String recommendationResult; // AI가 준 추천 텍스트 원문 (파싱 안 하고 그대로)

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    private AiSkillRecommendation(Long userId, String inputText, String recommendationResult) {
        this.userId = userId;
        this.inputText = inputText;
        this.recommendationResult = recommendationResult;
    }

    public static AiSkillRecommendation of(Long userId, String inputText, String recommendationResult) {
        return new AiSkillRecommendation(userId, inputText, recommendationResult);
    }
}
