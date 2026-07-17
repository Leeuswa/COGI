package idu.sba.backend.domain.repo.controller;

import idu.sba.backend.domain.repo.dto.GithubRepoResponseDTO;
import idu.sba.backend.domain.repo.dto.MyLinkedRepoResponseDTO;
import idu.sba.backend.domain.repo.dto.RepoLinkResponseDTO;
import idu.sba.backend.domain.repo.service.GithubRepoLinkService;
import idu.sba.backend.domain.repo.service.RepoMemberService;
import idu.sba.backend.global.common.ApiResponse;
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

}
