
package idu.sba.backend.domain.growth.controller;

import idu.sba.backend.domain.growth.dto.GrowthCompareResponseDTO;
import idu.sba.backend.domain.growth.dto.GrowthTrendResponseDTO;
import idu.sba.backend.domain.growth.service.GrowthService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 팀(레포) 성장 추이 API (GRW-001, API-050).
 * team 테이블이 따로 없어 teamId를 곧 repoId로 해석한다.
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class GrowthController {

    private final GrowthService growthService;

    // 주간 발생/해결 추이 — memberId 지정 시 개인, 없으면 팀 총합
    @GetMapping("/{teamId}/growth-trend")
    public ApiResponse<List<GrowthTrendResponseDTO>> getGrowthTrend(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long teamId,
            @RequestParam(defaultValue = "4W") String period,
            @RequestParam(required = false) Long memberId) {
        return ApiResponse.ok(growthService.getGrowthTrend(userId, teamId, period, memberId));
    }

    // 팀장 전용 — 팀원별 발생 수 겹쳐보기(멀티 시리즈)
    @GetMapping("/{teamId}/growth-trend/compare")
    public ApiResponse<GrowthCompareResponseDTO> getGrowthCompare(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long teamId,
            @RequestParam(defaultValue = "4w") String period){
        return ApiResponse.ok(growthService.getGrowthCompare(userId,teamId,period));
    }
}