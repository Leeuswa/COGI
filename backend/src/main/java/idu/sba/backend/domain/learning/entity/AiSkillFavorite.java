package idu.sba.backend.domain.learning.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 즐겨찾기한 스킬. 나중에 리뷰 스튜디오 후속질문에서 꺼내 쓸 수 있게 별도 표로 둔다.
@Entity
@Table(name = "ai_skill_favorites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSkillFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long skillId;

    private AiSkillFavorite(Long userId, Long skillId) {
        this.userId = userId;
        this.skillId = skillId;
    }

    public static AiSkillFavorite of(Long userId, Long skillId) {
        return new AiSkillFavorite(userId, skillId);
    }
}
