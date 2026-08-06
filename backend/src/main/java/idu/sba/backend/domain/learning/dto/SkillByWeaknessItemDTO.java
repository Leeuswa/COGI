package idu.sba.backend.domain.learning.dto;

// 약점 기반 스킬 추천(신규) 한 건 — provider는 CLAUDE/CHATGPT/GEMINI/COPILOT 중 하나
// skillId·isFavorite는 ai_skills에 저장한 뒤 채워 넣는다 (AI 응답 파싱 시점엔 없음 → withSkill로 보충)
// 컴포넌트 이름을 isFavorite로 그대로 둬서 JSON 키도 isFavorite로 나간다 (record 접근자는 이름 그대로 나가서 별도 매핑이 필요없다)
public record SkillByWeaknessItemDTO(String provider, String title, String why, String howTo, String prompt,
                                     Long skillId, boolean isFavorite) {

    // AI 파싱 직후에는 skillId·isFavorite가 없어서 null/false로 우선 만든다
    public static SkillByWeaknessItemDTO parsed(String provider, String title, String why, String howTo, String prompt) {
        return new SkillByWeaknessItemDTO(provider, title, why, howTo, prompt, null, false);
    }

    // ai_skills에 저장한 뒤 그 skillId·즐겨찾기 여부를 채워 새로 만든다 (record라 불변 — with류로 복사)
    public SkillByWeaknessItemDTO withSkill(Long skillId, boolean isFavorite) {
        return new SkillByWeaknessItemDTO(provider, title, why, howTo, prompt, skillId, isFavorite);
    }
}
