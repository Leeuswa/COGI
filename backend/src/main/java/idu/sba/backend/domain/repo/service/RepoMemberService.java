package idu.sba.backend.domain.repo.service;

import idu.sba.backend.domain.repo.dto.RepoInvitationResponseDTO;
import idu.sba.backend.domain.repo.dto.RepoInviteRequestDTO;
import idu.sba.backend.domain.repo.dto.RepoMemberResponseDTO;

public interface RepoMemberService {

    //팀장이 GitHub 아이디로 팀원 초대(API-029, FR-36)
    RepoInvitationResponseDTO inviteMember(Long currentUserId, Long repoId, RepoInviteRequestDTO request);

    //팀원 초대 수락(API-030, FR-37)
    RepoMemberResponseDTO acceptInvite(Long currentUserId, Long repoId, Long inviteId);

    //팀원 초대 거절(API-030-1, FR-37)
    void rejectInvite(Long currentUserId, Long repoId, Long inviteId);

}
