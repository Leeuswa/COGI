package idu.sba.backend.domain.learning.dto;

// 약점 기반 스킬 추천(신규) 한 건 — provider는 CLAUDE/CHATGPT/GEMINI/COPILOT 중 하나
public record SkillByWeaknessItemDTO(String provider, String title, String why, String howTo, String prompt) {
}
