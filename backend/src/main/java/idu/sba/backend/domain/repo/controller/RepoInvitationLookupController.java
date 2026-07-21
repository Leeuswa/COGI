package idu.sba.backend.domain.repo.controller;

import idu.sba.backend.domain.repo.dto.RepoInvitationLookupResponseDTO;
import idu.sba.backend.domain.repo.service.RepoMemberService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//이메일 초대 링크(/repo-invites/accept?token=...) 랜딩페이지 전용 — 비로그인 상태에서 호출됨
@RestController
@RequestMapping("/api/repo-invitations")
@RequiredArgsConstructor
public class RepoInvitationLookupController {

    private final RepoMemberService repoMemberService;

    @GetMapping("/by-token/{token}")
    public ApiResponse<RepoInvitationLookupResponseDTO> lookupByToken(@PathVariable String token) {
        return ApiResponse.ok(repoMemberService.lookupInvitationByToken(token));
    }

}
