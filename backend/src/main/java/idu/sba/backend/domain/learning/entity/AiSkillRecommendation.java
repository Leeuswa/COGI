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

    // length를 안 주면 @Lob이 tinytext(255B)로 매핑돼 답변 몇 줄에 Data too long이 난다.
    // 퀴즈 explain에서 똑같이 당한 적 있다 (HANDOFF 6절)
    @Lob
    @Column(nullable = false, length = 65535)
    private String inputText; // 사용자가 적은 약점/요구사항 원문

    @Lob
    @Column(nullable = false, length = 65535)
    private String recommendationResult; // AI가 준 추천 텍스트 원문 (파싱 안 하고 그대로)

    private String kind; // FREE_TEXT: 자유 입력 추천(평문), BY_WEAKNESS: 약점 기반 추천(JSON) — latest 조회에서 구분용. 옛 행은 null

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    private AiSkillRecommendation(Long userId, String inputText, String recommendationResult, String kind) {
        this.userId = userId;
        this.inputText = inputText;
        this.recommendationResult = recommendationResult;
        this.kind = kind;
    }

    public static AiSkillRecommendation of(Long userId, String inputText, String recommendationResult, String kind) {
        return new AiSkillRecommendation(userId, inputText, recommendationResult, kind);
    }
}
