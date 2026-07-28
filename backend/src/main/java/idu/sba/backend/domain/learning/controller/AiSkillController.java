package idu.sba.backend.domain.learning.controller;

import idu.sba.backend.domain.learning.dto.AiSkillResponseDTO;
import idu.sba.backend.domain.learning.service.LearningService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// AI 스킬 추천 (LRN-005). 큐레이션 데이터를 읽어 주는 게 전부라 AI 호출도 크레딧 소모도 없다.
@RestController
@RequestMapping("/api/ai-skills")
@RequiredArgsConstructor
public class AiSkillController {

    private final LearningService learningService;

    // 내가 쓰는 AI를 고르면 그 AI에 맞는 스킬만 내려준다
    @GetMapping
    public ApiResponse<List<AiSkillResponseDTO>> list(@AuthenticationPrincipal Long userId,
                                                      @RequestParam(defaultValue = "CLAUDE") String provider) {
        return ApiResponse.ok(learningService.getAiSkills(userId, provider));
    }

    // 즐겨찾기 토글 — 나중에 리뷰 스튜디오 후속질문에서 꺼내 쓸 목록이 된다
    @PostMapping("/{skillId}/favorite")
    public ApiResponse<Void> toggleFavorite(@AuthenticationPrincipal Long userId, @PathVariable Long skillId) {
        learningService.toggleSkillFavorite(userId, skillId);
        return ApiResponse.ok("즐겨찾기가 변경되었습니다.");
    }
}
