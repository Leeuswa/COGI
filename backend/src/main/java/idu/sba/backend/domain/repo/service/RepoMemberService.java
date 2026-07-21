package idu.sba.backend.domain.repo.service;

import idu.sba.backend.domain.repo.dto.MyLinkedRepoResponseDTO;
import idu.sba.backend.domain.repo.dto.MyRepoInvitationResponseDTO;
import idu.sba.backend.domain.repo.dto.RepoInvitationLookupResponseDTO;
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

    //이메일 초대 링크 클릭 시 토큰으로 조회(비로그인, 케이스②·③ 랜딩페이지) — 그 이메일로 기존 계정 존재 여부까지 반환
    RepoInvitationLookupResponseDTO lookupInvitationByToken(String token);

    //GitHub 가입/연동 완료 시 대기 중인 초대를 자동으로 매칭·수락 처리(케이스③ 훅)
    void autoMatchPendingInvitations(User newUser);

    //레포 연동(API-023) 완료 시 등록자를 OWNER로 등록 — 레포 연동 API 담당자가 호출할 훅
    void registerOwner(Long repoId, Long ownerUserId);

    //레포(팀) 멤버 목록 — OWNER뿐 아니라 멤버면 누구나 조회 가능(API-031)
    List<RepoMemberResponseDTO> listMembers(Long currentUserId, Long repoId);

    //내가 속한 레포(팀) 목록 — OWNER든 MEMBER든 역할 무관, 소속된 레포 전부
    List<MyLinkedRepoResponseDTO> listMyRepos(Long currentUserId);

    //팀원이 스스로 팀 나가기 — 팀장은 위임 먼저 해야 함(OWNER_CANNOT_LEAVE)
    void leaveRepo(Long currentUserId, Long repoId);

    //팀장이 팀원 내보내기 — 자기 자신은 대상이 될 수 없음
    void removeMember(Long currentUserId, Long repoId, Long targetUserId);

    //팀장 위임 — 기존 팀장은 MEMBER로 강등, 대상은 OWNER로 승격
    RepoMemberResponseDTO transferOwnership(Long currentUserId, Long repoId, Long targetUserId);

}
