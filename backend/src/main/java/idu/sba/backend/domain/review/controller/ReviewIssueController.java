package idu.sba.backend.domain.review.controller;

import idu.sba.backend.domain.review.dto.IssueDecisionRequestDTO;
import idu.sba.backend.domain.review.dto.IssueFinalizeRequestDTO;
import idu.sba.backend.domain.review.dto.ReverifyIssueRequestDTO;
import idu.sba.backend.domain.review.dto.ReverifyIssueResponseDTO;
import idu.sba.backend.domain.review.service.ReviewIssueService;
import idu.sba.backend.domain.review.service.ReviewService;
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
    private final ReviewService reviewService; //reverifyIssue만 여기 위임 — AI 호출 인프라가 이미 ReviewServiceImpl에 있어서 중복 안 만듦

    // [설계 추론] 스튜디오 판정 확정(요구 #7) — PR 리뷰의 CRITICAL 이슈만 팀장 승인 대기로 가고 나머진 바로 반영
    @PatchMapping("/{issueId}/finalize")
    public ApiResponse<Void> finalizeIssue(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long issueId,
            @Valid @RequestBody IssueFinalizeRequestDTO request) {
        reviewIssueService.finalizeIssue(userId, issueId, request.getVerdict());
        return ApiResponse.ok("이슈를 처리했어요.");
    }

    // 이슈 승인 흐름(RDB-003 [설계 추론]) — 레포 팀장만 호출 가능(NOT_REPO_OWNER)
    @PatchMapping("/{issueId}/decision")
    public ApiResponse<Void> decideResolveRequest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long issueId,
            @RequestBody IssueDecisionRequestDTO request) {
        reviewIssueService.decideResolveRequest(userId, issueId, request.isApprove());
        return ApiResponse.ok(request.isApprove() ? "승인했어요." : "반려했어요.");
    }

    // 이슈 재검증 [설계 추론] — 참고용 판정만 돌려주고 이슈 status는 안 바꿈(반영은 finalize를 따로 눌러야 함)
    @PostMapping("/{issueId}/reverify")
    public ApiResponse<ReverifyIssueResponseDTO> reverifyIssue(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long issueId,
            @Valid @RequestBody ReverifyIssueRequestDTO request) {
        return ApiResponse.ok(reviewService.reverifyIssue(userId, issueId, request.getFixedCode()));
    }

}
