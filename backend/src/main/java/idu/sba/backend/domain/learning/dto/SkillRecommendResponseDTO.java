package idu.sba.backend.domain.learning.dto;

import lombok.Getter;

// AI 스킬 추천 응답 — AI가 준 텍스트를 그대로 담는다. JSON 스키마를 안 씌우니 파싱이 깨질 일이 없다
@Getter
public class SkillRecommendResponseDTO {

    private final String result;

    private SkillRecommendResponseDTO(String result) {
        this.result = result;
    }

    public static SkillRecommendResponseDTO of(String result) {
        return new SkillRecommendResponseDTO(result);
    }
}
