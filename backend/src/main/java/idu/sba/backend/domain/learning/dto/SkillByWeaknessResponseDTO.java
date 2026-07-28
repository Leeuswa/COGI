package idu.sba.backend.domain.learning.dto;

import java.util.List;

// 약점 기반 AI별 스킬 추천(신규) 응답 — weaknesses는 이번에 반영된 약점 카테고리, items는 도구별 추천 4건
public record SkillByWeaknessResponseDTO(List<String> weaknesses, List<SkillByWeaknessItemDTO> items) {
}
