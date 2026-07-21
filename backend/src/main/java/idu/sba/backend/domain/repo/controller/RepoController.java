package idu.sba.backend.domain.repo.controller;

import idu.sba.backend.domain.pr.dto.PrFileResponseDTO;
import idu.sba.backend.domain.pr.dto.PrListItemResponseDTO;
import idu.sba.backend.domain.pr.dto.PrReviewImportRequestDTO;
import idu.sba.backend.domain.pr.dto.PrSummaryResponseDTO;
import idu.sba.backend.domain.pr.service.PrService;
import idu.sba.backend.domain.repo.dto.GithubRepoResponseDTO;
import idu.sba.backend.domain.repo.dto.MyLinkedRepoResponseDTO;
import idu.sba.backend.domain.repo.dto.RepoLinkResponseDTO;
import idu.sba.backend.domain.repo.service.GithubRepoLinkService;
import idu.sba.backend.domain.repo.service.RepoMemberService;
import idu.sba.backend.domain.review.dto.ReviewResultResponseDTO;
import idu.sba.backend.domain.review.service.ReviewService;
import idu.sba.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class RepoController {

    private final GithubRepoLinkService githubRepoLinkService;
    private final RepoMemberService repoMemberService;
    private final PrService prService;
    private final ReviewService reviewService;

    //API-022: 내 GitHub 레포 목록 조회(private 포함)
    @GetMapping("/github")
    public ApiResponse<List<GithubRepoResponseDTO>> listMyGithubRepos(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(githubRepoLinkService.listMyGithubRepos(userId));
    }

    //내가 속한 레포(팀) 목록(OWNER·MEMBER 무관) — 팀 화면이 "내 팀"을 보여줄 때 씀
    @GetMapping("/me")
    public ApiResponse<List<MyLinkedRepoResponseDTO>> listMyRepos(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(repoMemberService.listMyRepos(userId));
    }

    //API-023: 레포 선택 및 저장(Webhook 자동 등록은 별도 작업에서 이어붙임)
    @PostMapping("/{repoId}/link")
    public ResponseEntity<ApiResponse<RepoLinkResponseDTO>> linkRepo(
            @AuthenticationPrincipal Long userId,
            @PathVariable String repoId) {
        RepoLinkResponseDTO result = githubRepoLinkService.linkRepo(userId, repoId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("레포를 연동했습니다.", result));
    }

    // Studio "PR 가져오기" 피커 1단계 — 레포의 열린 PR 목록(GitHub 실시간 조회)
    @GetMapping("/{repoId}/prs")
    public ApiResponse<List<PrSummaryResponseDTO>> listRepoPrs(
            @AuthenticationPrincipal Long userId, @PathVariable Long repoId) {
        return ApiResponse.ok(prService.listOpenPrs(userId, repoId));
    }

    // Studio "PR 가져오기" 피커 2단계 — 특정 PR의 변경 파일 목록
    @GetMapping("/{repoId}/prs/{prNumber}/files")
    public ApiResponse<List<PrFileResponseDTO>> listRepoPrFiles(
            @AuthenticationPrincipal Long userId, @PathVariable Long repoId, @PathVariable int prNumber) {
        return ApiResponse.ok(prService.listPrFiles(userId, repoId, prNumber));
    }

    // Studio "PR 가져오기" 피커 3단계(리뷰 실행) [설계 추론] — target_type=PR로 저장돼 아래 목록/API-025에도 뜬다
    @PostMapping("/{repoId}/prs/{prNumber}/review")
    public ApiResponse<ReviewResultResponseDTO> reviewImportedPr(
            @AuthenticationPrincipal Long userId, @PathVariable Long repoId, @PathVariable Integer prNumber,
            @Valid @RequestBody PrReviewImportRequestDTO request) {
        return ApiResponse.ok(reviewService.createFromPrImport(userId, repoId, prNumber, request));
    }

    // PR 리뷰 대시보드(API-032, 팀 대신 레포 기준으로 축소) [설계 추론] — 우리 DB에 실제 리뷰가 기록된 PR 목록
    @GetMapping("/{repoId}/prs/reviewed")
    public ApiResponse<List<PrListItemResponseDTO>> listReviewedPrs(
            @AuthenticationPrincipal Long userId, @PathVariable Long repoId) {
        return ApiResponse.ok(prService.listReviewedPrs(userId, repoId));
    }

}
