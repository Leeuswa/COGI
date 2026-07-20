package idu.sba.backend.domain.review.controller;

import idu.sba.backend.domain.review.dto.IssueFinalizeRequestDTO;
import idu.sba.backend.domain.review.service.ReviewIssueService;
import idu.sba.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class ReviewIssueController {

    private final ReviewIssueService reviewIssueService;

    // [설계 추론] 스튜디오 판정 확정(요구 #7) — 팀장 승인 없이 바로 반영
    @PatchMapping("/{issueId}/finalize")
    public ApiResponse<Void> finalizeIssue(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long issueId,
            @Valid @RequestBody IssueFinalizeRequestDTO request) {
        reviewIssueService.finalizeIssue(userId, issueId, request.getVerdict());
        return ApiResponse.ok("이슈를 처리했어요.");
    }

}
