package idu.sba.backend.domain.repo.controller;

import idu.sba.backend.domain.repo.dto.RepoInvitationResponseDTO;
import idu.sba.backend.domain.repo.dto.RepoInviteRequestDTO;
import idu.sba.backend.domain.repo.dto.RepoMemberResponseDTO;
import idu.sba.backend.domain.repo.service.RepoMemberService;
import idu.sba.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repos/{repoId}/members")
@RequiredArgsConstructor
public class RepoMemberController {

    private final RepoMemberService repoMemberService;

    //API-029: 팀장이 GitHub 아이디로 팀원 초대
    @PostMapping("/invite")
    public ResponseEntity<ApiResponse<RepoInvitationResponseDTO>> inviteMember(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long repoId,
            @Valid @RequestBody RepoInviteRequestDTO request) {
        RepoInvitationResponseDTO invitation = repoMemberService.inviteMember(userId, repoId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("초대를 보냈습니다.", invitation));
    }

    //API-030: 팀원 초대 수락
    @PostMapping("/invitations/{inviteId}/accept")
    public ApiResponse<RepoMemberResponseDTO> acceptInvite(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long repoId,
            @PathVariable Long inviteId) {
        return ApiResponse.ok("초대를 수락했습니다.", repoMemberService.acceptInvite(userId, repoId, inviteId));
    }

    //API-030-1: 팀원 초대 거절
    @PostMapping("/invitations/{inviteId}/reject")
    public ApiResponse<Void> rejectInvite(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long repoId,
            @PathVariable Long inviteId) {
        repoMemberService.rejectInvite(userId, repoId, inviteId);
        return ApiResponse.ok("초대를 거절했습니다.");
    }

}
