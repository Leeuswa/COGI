package idu.sba.backend.domain.learning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import idu.sba.backend.domain.learning.entity.AiSkill;
import lombok.Getter;

// AI 스킬 한 장 — 프론트 Skills.tsx가 읽는 필드명에 맞춤
@Getter
public class AiSkillResponseDTO {

    private final Long id;
    private final String provider;
    private final String category;
    private final String title;
    private final String description;
    private final String howTo;
    private final String url;

    @JsonProperty("isFavorite") // 프론트가 읽는 키로 고정
    private final boolean favorite;

    private AiSkillResponseDTO(AiSkill skill, boolean favorite) {
        this.id = skill.getId();
        this.provider = skill.getProvider();
        this.category = skill.getCategory();
        this.title = skill.getTitle();
        this.description = skill.getDescription();
        this.howTo = skill.getHowTo();
        this.url = skill.getUrl();
        this.favorite = favorite;
    }

    public static AiSkillResponseDTO of(AiSkill skill, boolean favorite) {
        return new AiSkillResponseDTO(skill, favorite);
    }
}
