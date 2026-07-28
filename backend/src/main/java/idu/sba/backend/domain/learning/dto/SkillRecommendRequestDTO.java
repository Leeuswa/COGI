package idu.sba.backend.domain.learning.dto;

import lombok.Getter;

// AI 스킬 추천 요청 (API-049) — 코드리뷰 후 남은 약점이나 하고 싶은 요구사항 한 문단
@Getter
public class SkillRecommendRequestDTO {
    private String weaknessOrRequirement;
}
