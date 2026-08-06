package idu.sba.backend.domain.repo.controller;

import idu.sba.backend.domain.repo.dto.MyRepoInvitationResponseDTO;
import idu.sba.backend.domain.repo.service.RepoMemberService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me/repo-invitations")
@RequiredArgsConstructor
public class MyRepoInvitationController {

    private final RepoMemberService repoMemberService;

    //내가 받은 대기 중인 레포 초대 목록(인앱 알림)
    @GetMapping
    public ApiResponse<List<MyRepoInvitationResponseDTO>> listMyInvitations(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(repoMemberService.listMyInvitations(userId));
    }

}
