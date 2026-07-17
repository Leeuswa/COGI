package idu.sba.backend.domain.repo.service;

import idu.sba.backend.domain.repo.dto.MyLinkedRepoResponseDTO;
import idu.sba.backend.domain.repo.dto.MyRepoInvitationResponseDTO;
import idu.sba.backend.domain.repo.dto.RepoInvitationResponseDTO;
import idu.sba.backend.domain.repo.dto.RepoInviteRequestDTO;
import idu.sba.backend.domain.repo.dto.RepoMemberResponseDTO;
import idu.sba.backend.domain.repo.entity.GithubRepository;
import idu.sba.backend.domain.repo.entity.RepoInvitation;
import idu.sba.backend.domain.repo.entity.RepoInvitationStatus;
import idu.sba.backend.domain.repo.entity.RepoMember;
import idu.sba.backend.domain.repo.entity.RepoRole;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.repo.repository.RepoInvitationRepository;
import idu.sba.backend.domain.repo.repository.RepoMemberRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RepoMemberServiceImpl implements RepoMemberService {

    //이메일 초대 링크 유효시간 72시간
    private static final int EMAIL_INVITE_TTL_HOURS = 72;

    private final RepoMemberRepository repoMemberRepository;
    private final RepoInvitationRepository repoInvitationRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public RepoInvitationResponseDTO inviteMember(Long currentUserId, Long repoId, RepoInviteRequestDTO request) {
        requireOwner(repoId, currentUserId);

        String githubUsername = request.getGithubUsername();
        if (repoInvitationRepository.existsByRepoIdAndInvitedGithubUsernameIgnoreCaseAndStatus(
                repoId, githubUsername, RepoInvitationStatus.PENDING)) {
            throw new BusinessException(ErrorCode.INVITATION_ALREADY_SENT);
        }

        Optional<User> matchedUser = userRepository.findByGithubUsername(githubUsername);

        //케이스① 가입 + GitHub 연동된 사용자를 즉시 찾은 경우 — invited_user_id를 초대 생성 즉시 매핑
        if (matchedUser.isPresent()) {
            Long matchedUserId = matchedUser.get().getId();
            if (repoMemberRepository.existsByRepoIdAndUserId(repoId, matchedUserId)) {
                throw new BusinessException(ErrorCode.ALREADY_REPO_MEMBER);
            }
            RepoInvitation invitation = repoInvitationRepository.save(
                    RepoInvitation.issueMatched(repoId, currentUserId, githubUsername, matchedUserId));
            return RepoInvitationResponseDTO.of(invitation);
        }

        //케이스② 미가입 또는 GitHub 미연동 — 이메일을 아직 안 받았으면 안내만 반환(프론트가 이메일 입력창을 띄움)
        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.GITHUB_USER_NOT_FOUND);
        }

        RepoInvitation invitation = repoInvitationRepository.save(
                RepoInvitation.issueByEmail(repoId, currentUserId, githubUsername, email, EMAIL_INVITE_TTL_HOURS));
        sendInviteMail(email, invitation.getInviteToken());

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

    @Override
    public List<MyRepoInvitationResponseDTO> listMyInvitations(Long currentUserId) {
        return repoInvitationRepository.findByInvitedUserIdAndStatus(currentUserId, RepoInvitationStatus.PENDING).stream()
                .map(invitation -> MyRepoInvitationResponseDTO.of(invitation, repoNameOf(invitation.getRepoId())))
                .toList();
    }

    @Override
    @Transactional
    public void autoMatchPendingInvitations(User newUser) {
        Map<Long, RepoInvitation> matched = new LinkedHashMap<>();

        if (newUser.getGithubUsername() != null) {
            repoInvitationRepository
                    .findByStatusAndInvitedUserIdIsNullAndInvitedGithubUsernameIgnoreCase(
                            RepoInvitationStatus.PENDING, newUser.getGithubUsername())
                    .forEach(invitation -> matched.put(invitation.getId(), invitation));
        }
        if (newUser.getEmail() != null) {
            repoInvitationRepository
                    .findByStatusAndInvitedUserIdIsNullAndInviteeEmailIgnoreCase(
                            RepoInvitationStatus.PENDING, newUser.getEmail())
                    .forEach(invitation -> matched.put(invitation.getId(), invitation));
        }

        for (RepoInvitation invitation : matched.values()) {
            if (invitation.isExpired()) {
                continue;
            }
            invitation.accept(newUser.getId());
            //이미 멤버면(중복 초대 등) 초대만 정리하고 멤버 row는 새로 만들지 않음
            if (!repoMemberRepository.existsByRepoIdAndUserId(invitation.getRepoId(), newUser.getId())) {
                repoMemberRepository.save(RepoMember.of(invitation.getRepoId(), newUser.getId(), RepoRole.MEMBER));
            }
        }
    }

    @Override
    @Transactional
    public void registerOwner(Long repoId, Long ownerUserId) {
        if (!repoMemberRepository.existsByRepoIdAndUserId(repoId, ownerUserId)) {
            repoMemberRepository.save(RepoMember.of(repoId, ownerUserId, RepoRole.OWNER));
        }
    }

    @Override
    public List<RepoMemberResponseDTO> listMembers(Long currentUserId, Long repoId) {
        requireMember(repoId, currentUserId);
        return repoMemberRepository.findByRepoId(repoId).stream()
                .map(member -> RepoMemberResponseDTO.of(member, userRepository.findById(member.getUserId()).orElseThrow()))
                .toList();
    }

    @Override
    public List<MyLinkedRepoResponseDTO> listMyRepos(Long currentUserId) {
        return repoMemberRepository.findByUserId(currentUserId).stream()
                .map(member -> githubRepositoryRepository.findById(member.getRepoId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(MyLinkedRepoResponseDTO::of)
                .toList();
    }

    @Override
    @Transactional
    public void leaveRepo(Long currentUserId, Long repoId) {
        RepoMember member = requireMember(repoId, currentUserId);
        if (member.getRole() == RepoRole.OWNER) {
            throw new BusinessException(ErrorCode.OWNER_CANNOT_LEAVE);
        }
        repoMemberRepository.delete(member);
    }

    @Override
    @Transactional
    public void removeMember(Long currentUserId, Long repoId, Long targetUserId) {
        requireOwner(repoId, currentUserId);
        if (targetUserId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_ACT_ON_SELF);
        }
        RepoMember target = requireMember(repoId, targetUserId);
        repoMemberRepository.delete(target);
    }

    @Override
    @Transactional
    public RepoMemberResponseDTO transferOwnership(Long currentUserId, Long repoId, Long targetUserId) {
        RepoMember owner = requireOwner(repoId, currentUserId);
        if (targetUserId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_ACT_ON_SELF);
        }
        RepoMember target = requireMember(repoId, targetUserId);

        owner.changeRole(RepoRole.MEMBER);
        target.changeRole(RepoRole.OWNER);

        User targetUser = userRepository.findById(targetUserId).orElseThrow();
        return RepoMemberResponseDTO.of(target, targetUser);
    }

    private String repoNameOf(Long repoId) {
        return githubRepositoryRepository.findById(repoId).map(GithubRepository::getRepoName).orElse(null);
    }

    private RepoMember requireOwner(Long repoId, Long userId) {
        RepoMember member = requireMember(repoId, userId);
        if (member.getRole() != RepoRole.OWNER) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_REPO_PERMISSION);
        }
        return member;
    }

    //역할 무관 — 이 레포의 멤버이기만 하면 통과(조회용)
    private RepoMember requireMember(Long repoId, Long userId) {
        return repoMemberRepository.findByRepoIdAndUserId(repoId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_REPO_MEMBER));
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

    //레포 초대 메일 발송(이메일 경로)
    private void sendInviteMail(String to, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject("[COGI] 레포 팀원 초대");
        message.setText("레포에 초대되었습니다. 아래 링크에서 GitHub 계정으로 가입/로그인해주세요.\n"
                + frontendUrl + "/repo-invites/accept?token=" + token);
        mailSender.send(message);
    }

}
