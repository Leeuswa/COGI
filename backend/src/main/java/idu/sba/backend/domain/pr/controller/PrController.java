package idu.sba.backend.domain.pr.controller;

import idu.sba.backend.domain.pr.dto.PrReviewResponseDTO;
import idu.sba.backend.domain.pr.service.PrService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//일반 JWT 인증 엔드포인트(웹훅과 달리 프론트가 직접 호출) — 나중에 API-028/032/035/038도 여기 추가 가능
@RestController
@RequestMapping("/api/prs")
@RequiredArgsConstructor
public class PrController {

    private final PrService prService;

    //API-025: PR 코드리뷰 결과(이슈 목록) 조회
    @GetMapping("/{prId}/review")
    public ApiResponse<PrReviewResponseDTO> getPrReview(
            @AuthenticationPrincipal Long userId, @PathVariable Long prId) {
        return ApiResponse.ok(prService.getPrReview(userId, prId));
    }

}
