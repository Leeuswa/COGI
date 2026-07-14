package idu.sba.backend.domain.repo.service;

import idu.sba.backend.domain.repo.dto.RepoInvitationResponseDTO;
import idu.sba.backend.domain.repo.dto.RepoInviteRequestDTO;
import idu.sba.backend.domain.repo.dto.RepoMemberResponseDTO;
import idu.sba.backend.domain.repo.entity.RepoInvitation;
import idu.sba.backend.domain.repo.entity.RepoInvitationStatus;
import idu.sba.backend.domain.repo.entity.RepoMember;
import idu.sba.backend.domain.repo.entity.RepoRole;
import idu.sba.backend.domain.repo.repository.RepoInvitationRepository;
import idu.sba.backend.domain.repo.repository.RepoMemberRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RepoMemberServiceImpl implements RepoMemberService {

    private final RepoMemberRepository repoMemberRepository;
    private final RepoInvitationRepository repoInvitationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RepoInvitationResponseDTO inviteMember(Long currentUserId, Long repoId, RepoInviteRequestDTO request) {
        requireOwner(repoId, currentUserId);

        RepoInvitation invitation = repoInvitationRepository.save(
                RepoInvitation.issue(repoId, currentUserId, request.getGithubUsername()));

        return RepoInvitationResponseDTO.of(invitation);
    }

    @Override
    @Transactional
    public RepoMemberResponseDTO acceptInvite(Long currentUserId, Long repoId, Long inviteId) {
        RepoInvitation invitation = getPendingInvitationOrThrow(repoId, inviteId);
        User user = requireMatchingGithubUser(currentUserId, invitation);

        if (repoMemberRepository.existsByRepoIdAndUserId(repoId, currentUserId)) {
            throw new BusinessException(ErrorCode.ALREADY_REPO_MEMBER);
        }

        invitation.accept(currentUserId);
        RepoMember member = repoMemberRepository.save(RepoMember.of(repoId, currentUserId, RepoRole.MEMBER));

        return RepoMemberResponseDTO.of(member, user);
    }

    @Override
    @Transactional
    public void rejectInvite(Long currentUserId, Long repoId, Long inviteId) {
        RepoInvitation invitation = getPendingInvitationOrThrow(repoId, inviteId);
        requireMatchingGithubUser(currentUserId, invitation);

        invitation.reject();
    }

    private void requireOwner(Long repoId, Long userId) {
        RepoMember member = repoMemberRepository.findByRepoIdAndUserId(repoId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_REPO_MEMBER));
        if (member.getRole() != RepoRole.OWNER) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_REPO_PERMISSION);
        }
    }

    private RepoInvitation getPendingInvitationOrThrow(Long repoId, Long inviteId) {
        RepoInvitation invitation = repoInvitationRepository.findById(inviteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
        if (!invitation.getRepoId().equals(repoId)) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_FOUND);
        }
        if (invitation.getStatus() != RepoInvitationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVITATION_ALREADY_RESPONDED);
        }
        return invitation;
    }

    //초대받은 GitHub 계정 본인인지 확인(로그인 계정의 githubUsername과 대조)
    private User requireMatchingGithubUser(Long currentUserId, RepoInvitation invitation) {
        User user = userRepository.findById(currentUserId).orElseThrow();
        if (user.getGithubUsername() == null
                || !user.getGithubUsername().equalsIgnoreCase(invitation.getInvitedGithubUsername())) {
            throw new BusinessException(ErrorCode.INVITATION_GITHUB_USERNAME_MISMATCH);
        }
        return user;
    }

}
