package idu.sba.backend.domain.repo.service;

import idu.sba.backend.domain.repo.dto.MyLinkedRepoResponseDTO;
import idu.sba.backend.domain.repo.dto.MyRepoInvitationResponseDTO;
import idu.sba.backend.domain.repo.dto.RepoInvitationResponseDTO;
import idu.sba.backend.domain.repo.dto.RepoInviteRequestDTO;
import idu.sba.backend.domain.repo.dto.RepoMemberResponseDTO;
import idu.sba.backend.domain.user.entity.User;

import java.util.List;

public interface RepoMemberService {

    //팀장이 GitHub 아이디로 팀원 초대(API-029, FR-36) — 대상 존재 여부에 따라 즉시매칭/이메일 경로로 분기
    RepoInvitationResponseDTO inviteMember(Long currentUserId, Long repoId, RepoInviteRequestDTO request);

    //팀원 초대 수락(API-030, FR-37)
    RepoMemberResponseDTO acceptInvite(Long currentUserId, Long repoId, Long inviteId);

    //팀원 초대 거절(API-030-1, FR-37)
    void rejectInvite(Long currentUserId, Long repoId, Long inviteId);

    //내가 받은 대기 중인 초대 목록(인앱 알림)
    List<MyRepoInvitationResponseDTO> listMyInvitations(Long currentUserId);

    //GitHub 가입/연동 완료 시 대기 중인 초대를 자동으로 매칭·수락 처리(케이스③ 훅)
    void autoMatchPendingInvitations(User newUser);

    //레포 연동(API-023) 완료 시 등록자를 OWNER로 등록 — 레포 연동 API 담당자가 호출할 훅
    void registerOwner(Long repoId, Long ownerUserId);

    //레포(팀) 멤버 목록 — OWNER뿐 아니라 멤버면 누구나 조회 가능(API-031)
    List<RepoMemberResponseDTO> listMembers(Long currentUserId, Long repoId);

    //내가 속한 레포(팀) 목록 — OWNER든 MEMBER든 역할 무관, 소속된 레포 전부
    List<MyLinkedRepoResponseDTO> listMyRepos(Long currentUserId);

}
