package idu.sba.backend.domain.learning.controller;

import idu.sba.backend.domain.learning.dto.AiSkillResponseDTO;
import idu.sba.backend.domain.learning.dto.SkillByWeaknessResponseDTO;
import idu.sba.backend.domain.learning.dto.SkillRecommendRequestDTO;
import idu.sba.backend.domain.learning.dto.SkillRecommendResponseDTO;
import idu.sba.backend.domain.learning.service.LearningService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// AI 스킬 추천 (LRN-005). 목록·즐겨찾기는 큐레이션 데이터라 AI 호출도 크레딧 소모도 없고,
// 추천(API-049)만 실제로 AI를 불러 크레딧을 쓴다. 경로 앞부분이 서로 달라 클래스 레벨 매핑은 쓰지 않는다.
@RestController
@RequiredArgsConstructor
public class AiSkillController {

    private final LearningService learningService;

    // 내가 쓰는 AI를 고르면 그 AI에 맞는 스킬만 내려준다. category를 같이 주면 그 안에서 한 번 더 거른다
    @GetMapping("/api/ai-skills")
    public ApiResponse<List<AiSkillResponseDTO>> list(@AuthenticationPrincipal Long userId,
                                                      @RequestParam(defaultValue = "CLAUDE") String provider,
                                                      @RequestParam(required = false) String category) {
        return ApiResponse.ok(learningService.getAiSkills(userId, provider, category));
    }

    // 내가 찜한 스킬 전체 — provider 안 가리고 전부 내려준다 (경로변수 매핑과 안 겹치게 /favorites 고정 세그먼트로 둠)
    @GetMapping("/api/ai-skills/favorites")
    public ApiResponse<List<AiSkillResponseDTO>> favorites(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(learningService.getFavoriteSkills(userId));
    }

    // 즐겨찾기 토글 — 나중에 리뷰 스튜디오 후속질문에서 꺼내 쓸 목록이 된다
    @PostMapping("/api/ai-skills/{skillId}/favorite")
    public ApiResponse<Void> toggleFavorite(@AuthenticationPrincipal Long userId, @PathVariable Long skillId) {
        learningService.toggleSkillFavorite(userId, skillId);
        return ApiResponse.ok("즐겨찾기가 변경되었습니다.");
    }

    // 약점/요구사항을 넣으면 어떤 AI의 어떤 스킬을 쓰면 좋을지 추천 (API-049). 이건 AI를 실제로 불러 크레딧을 쓴다
    @PostMapping("/api/ai-skill-recommendations")
    public ApiResponse<SkillRecommendResponseDTO> recommend(@AuthenticationPrincipal Long userId,
                                                            @RequestBody SkillRecommendRequestDTO request) {
        return ApiResponse.ok(learningService.recommendSkill(userId, request));
    }

    // 입력 없이 내 약점 통계로 AI별(CLAUDE/CHATGPT/GEMINI/COPILOT) 스킬을 하나씩 추천 (신규). 고정 크레딧 2를 쓴다
    @PostMapping("/api/ai-skill-recommendations/by-weakness")
    public ApiResponse<SkillByWeaknessResponseDTO> recommendByWeakness(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(learningService.recommendSkillByWeakness(userId));
    }
}
