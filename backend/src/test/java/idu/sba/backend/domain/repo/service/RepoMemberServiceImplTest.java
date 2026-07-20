package idu.sba.backend.domain.repo.service;

import idu.sba.backend.domain.repo.dto.RepoInvitationResponseDTO;
import idu.sba.backend.domain.repo.dto.RepoInviteRequestDTO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepoMemberServiceImplTest {

    @Mock private RepoMemberRepository repoMemberRepository;
    @Mock private RepoInvitationRepository repoInvitationRepository;
    @Mock private GithubRepositoryRepository githubRepositoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private JavaMailSender mailSender;

    @InjectMocks
    private RepoMemberServiceImpl service;

    private static final Long REPO_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long MEMBER_ID = 20L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "mailFrom", "noreply@cogi.test");
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:5173");
        //save()가 그대로 인자를 돌려주도록(실제 DB의 auto-increment id 채번은 흉내내지 않음)
        lenient().when(repoMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(repoInvitationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private RepoMember owner() {
        return RepoMember.of(REPO_ID, OWNER_ID, RepoRole.OWNER);
    }

    private User userWithGithub(Long id, String githubUsername, String email) {
        User user = User.createByGithub("gh-" + id, githubUsername, email, "token");
        setField(user, "id", id);
        return user;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private RepoInviteRequestDTO inviteRequest(String githubUsername, String email) {
        RepoInviteRequestDTO dto = new RepoInviteRequestDTO();
        setField(dto, "githubUsername", githubUsername);
        setField(dto, "email", email);
        return dto;
    }

    // ---------- inviteMember ----------

    @Test
    void 초대자가_레포_멤버가_아니면_NOT_REPO_MEMBER() {
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inviteMember(OWNER_ID, REPO_ID, inviteRequest("target-gh", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_REPO_MEMBER);
    }

    @Test
    void 초대자가_MEMBER면_INSUFFICIENT_REPO_PERMISSION() {
        RepoMember member = RepoMember.of(REPO_ID, MEMBER_ID, RepoRole.MEMBER);
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.inviteMember(MEMBER_ID, REPO_ID, inviteRequest("target-gh", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_REPO_PERMISSION);
    }

    @Test
    void 같은_사람한테_이미_PENDING_초대가_있으면_INVITATION_ALREADY_SENT() {
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, OWNER_ID)).thenReturn(Optional.of(owner()));
        when(repoInvitationRepository.existsByRepoIdAndInvitedGithubUsernameIgnoreCaseAndStatus(
                REPO_ID, "target-gh", RepoInvitationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.inviteMember(OWNER_ID, REPO_ID, inviteRequest("target-gh", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITATION_ALREADY_SENT);

        verify(repoInvitationRepository, never()).save(any());
    }

    @Test
    void GitHub_아이디로_기존_사용자를_찾으면_즉시_매핑되고_이메일은_안보낸다() {
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, OWNER_ID)).thenReturn(Optional.of(owner()));
        User target = userWithGithub(MEMBER_ID, "target-gh", "target@test.com");
        when(userRepository.findByGithubUsername("target-gh")).thenReturn(Optional.of(target));
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, MEMBER_ID)).thenReturn(false);

        RepoInvitationResponseDTO result = service.inviteMember(OWNER_ID, REPO_ID, inviteRequest("target-gh", null));

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.isViaEmail()).isFalse();
        verify(mailSender, never()).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void 매칭된_사용자가_이미_멤버면_ALREADY_REPO_MEMBER() {
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, OWNER_ID)).thenReturn(Optional.of(owner()));
        User target = userWithGithub(MEMBER_ID, "target-gh", "target@test.com");
        when(userRepository.findByGithubUsername("target-gh")).thenReturn(Optional.of(target));
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, MEMBER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.inviteMember(OWNER_ID, REPO_ID, inviteRequest("target-gh", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_REPO_MEMBER);
    }

    @Test
    void GitHub_아이디로_못찾고_이메일도_없으면_GITHUB_USER_NOT_FOUND() {
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, OWNER_ID)).thenReturn(Optional.of(owner()));
        when(userRepository.findByGithubUsername("unknown-gh")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inviteMember(OWNER_ID, REPO_ID, inviteRequest("unknown-gh", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GITHUB_USER_NOT_FOUND);
    }

    @Test
    void GitHub_아이디로_못찾고_이메일이_있으면_이메일_초대_발급_및_메일발송() {
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, OWNER_ID)).thenReturn(Optional.of(owner()));
        when(userRepository.findByGithubUsername("unknown-gh")).thenReturn(Optional.empty());

        RepoInvitationResponseDTO result = service.inviteMember(
                OWNER_ID, REPO_ID, inviteRequest("unknown-gh", "invitee@test.com"));

        assertThat(result.isViaEmail()).isTrue();
        verify(mailSender, times(1)).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    // ---------- acceptInvite / rejectInvite ----------

    private RepoInvitation pendingInvitationTo(String githubUsername) {
        return RepoInvitation.issueByEmail(REPO_ID, OWNER_ID, githubUsername, "x@test.com", 72);
    }

    @Test
    void 존재하지_않는_초대는_INVITATION_NOT_FOUND() {
        when(repoInvitationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptInvite(MEMBER_ID, REPO_ID, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITATION_NOT_FOUND);
    }

    @Test
    void 초대의_repoId가_다르면_INVITATION_NOT_FOUND() {
        RepoInvitation invitation = pendingInvitationTo("target-gh");
        when(repoInvitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.acceptInvite(MEMBER_ID, 999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITATION_NOT_FOUND);
    }

    @Test
    void 이미_응답한_초대는_INVITATION_ALREADY_RESPONDED() {
        RepoInvitation invitation = pendingInvitationTo("target-gh");
        invitation.reject();
        when(repoInvitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.acceptInvite(MEMBER_ID, REPO_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITATION_ALREADY_RESPONDED);
    }

    @Test
    void 초대받은_GitHub_계정이_아니면_INVITATION_GITHUB_USERNAME_MISMATCH() {
        RepoInvitation invitation = pendingInvitationTo("target-gh");
        when(repoInvitationRepository.findById(1L)).thenReturn(Optional.of(invitation));
        User wrongUser = userWithGithub(MEMBER_ID, "other-gh", "other@test.com");
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(wrongUser));

        assertThatThrownBy(() -> service.acceptInvite(MEMBER_ID, REPO_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITATION_GITHUB_USERNAME_MISMATCH);
    }

    @Test
    void 정상_수락하면_ACCEPTED_및_MEMBER_생성() {
        RepoInvitation invitation = pendingInvitationTo("target-gh");
        when(repoInvitationRepository.findById(1L)).thenReturn(Optional.of(invitation));
        User target = userWithGithub(MEMBER_ID, "target-gh", "target@test.com");
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(target));
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, MEMBER_ID)).thenReturn(false);

        var result = service.acceptInvite(MEMBER_ID, REPO_ID, 1L);

        assertThat(result.getRole()).isEqualTo("MEMBER");
        assertThat(invitation.getStatus()).isEqualTo(RepoInvitationStatus.ACCEPTED);
        verify(repoMemberRepository).save(any(RepoMember.class));
    }

    @Test
    void 정상_거절하면_REJECTED() {
        RepoInvitation invitation = pendingInvitationTo("target-gh");
        when(repoInvitationRepository.findById(1L)).thenReturn(Optional.of(invitation));
        User target = userWithGithub(MEMBER_ID, "target-gh", "target@test.com");
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(target));

        service.rejectInvite(MEMBER_ID, REPO_ID, 1L);

        assertThat(invitation.getStatus()).isEqualTo(RepoInvitationStatus.REJECTED);
        verify(repoMemberRepository, never()).save(any());
    }

    // ---------- autoMatchPendingInvitations ----------

    @Test
    void 자동매칭_githubUsername으로_매칭되면_수락되고_멤버가_생성된다() {
        User newUser = userWithGithub(MEMBER_ID, "new-gh", "new@test.com");
        RepoInvitation invitation = pendingInvitationTo("new-gh");
        when(repoInvitationRepository.findByStatusAndInvitedUserIdIsNullAndInvitedGithubUsernameIgnoreCase(
                RepoInvitationStatus.PENDING, "new-gh")).thenReturn(List.of(invitation));
        when(repoInvitationRepository.findByStatusAndInvitedUserIdIsNullAndInviteeEmailIgnoreCase(
                RepoInvitationStatus.PENDING, "new@test.com")).thenReturn(List.of());
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, MEMBER_ID)).thenReturn(false);

        service.autoMatchPendingInvitations(newUser);

        assertThat(invitation.getStatus()).isEqualTo(RepoInvitationStatus.ACCEPTED);
        verify(repoMemberRepository).save(any(RepoMember.class));
    }

    @Test
    void 자동매칭_만료된_초대는_스킵된다() {
        User newUser = userWithGithub(MEMBER_ID, "new-gh", "new@test.com");
        RepoInvitation expired = RepoInvitation.issueByEmail(REPO_ID, OWNER_ID, "new-gh", "new@test.com", -1);
        when(repoInvitationRepository.findByStatusAndInvitedUserIdIsNullAndInvitedGithubUsernameIgnoreCase(
                RepoInvitationStatus.PENDING, "new-gh")).thenReturn(List.of(expired));
        when(repoInvitationRepository.findByStatusAndInvitedUserIdIsNullAndInviteeEmailIgnoreCase(
                RepoInvitationStatus.PENDING, "new@test.com")).thenReturn(List.of());

        service.autoMatchPendingInvitations(newUser);

        assertThat(expired.getStatus()).isEqualTo(RepoInvitationStatus.PENDING);
        verify(repoMemberRepository, never()).save(any());
    }

    @Test
    void 자동매칭_이미_멤버면_초대만_수락되고_중복_멤버row는_생성안한다() {
        User newUser = userWithGithub(MEMBER_ID, "new-gh", "new@test.com");
        RepoInvitation invitation = pendingInvitationTo("new-gh");
        when(repoInvitationRepository.findByStatusAndInvitedUserIdIsNullAndInvitedGithubUsernameIgnoreCase(
                RepoInvitationStatus.PENDING, "new-gh")).thenReturn(List.of(invitation));
        when(repoInvitationRepository.findByStatusAndInvitedUserIdIsNullAndInviteeEmailIgnoreCase(
                RepoInvitationStatus.PENDING, "new@test.com")).thenReturn(List.of());
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, MEMBER_ID)).thenReturn(true);

        service.autoMatchPendingInvitations(newUser);

        assertThat(invitation.getStatus()).isEqualTo(RepoInvitationStatus.ACCEPTED);
        verify(repoMemberRepository, never()).save(any());
    }

    // ---------- registerOwner ----------

    @Test
    void registerOwner_이미_멤버면_생성하지_않는다() {
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, OWNER_ID)).thenReturn(true);

        service.registerOwner(REPO_ID, OWNER_ID);

        verify(repoMemberRepository, never()).save(any());
    }

    @Test
    void registerOwner_멤버가_아니면_OWNER로_등록한다() {
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, OWNER_ID)).thenReturn(false);

        service.registerOwner(REPO_ID, OWNER_ID);

        verify(repoMemberRepository).save(any(RepoMember.class));
    }

    // ---------- listMembers ----------

    @Test
    void listMembers_레포_멤버가_아니면_NOT_REPO_MEMBER() {
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listMembers(MEMBER_ID, REPO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_REPO_MEMBER);
    }

    @Test
    void listMembers_멤버면_OWNER도_MEMBER도_전부_조회된다() {
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, MEMBER_ID)).thenReturn(Optional.of(RepoMember.of(REPO_ID, MEMBER_ID, RepoRole.MEMBER)));
        when(repoMemberRepository.findByRepoId(REPO_ID)).thenReturn(List.of(owner(), RepoMember.of(REPO_ID, MEMBER_ID, RepoRole.MEMBER)));
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(userWithGithub(OWNER_ID, "owner-gh", "owner@test.com")));
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(userWithGithub(MEMBER_ID, "member-gh", "member@test.com")));

        var result = service.listMembers(MEMBER_ID, REPO_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting("githubUsername").containsExactlyInAnyOrder("owner-gh", "member-gh");
        assertThat(result).extracting("role").containsExactlyInAnyOrder("OWNER", "MEMBER");
    }

    // ---------- listMyRepos ----------

    @Test
    void listMyRepos_OWNER든_MEMBER든_소속된_레포를_전부_반환한다() {
        RepoMember asOwner = RepoMember.of(REPO_ID, MEMBER_ID, RepoRole.OWNER);
        RepoMember asMember = RepoMember.of(2L, MEMBER_ID, RepoRole.MEMBER);
        when(repoMemberRepository.findByUserId(MEMBER_ID)).thenReturn(List.of(asOwner, asMember));
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(
                GithubRepository.link(MEMBER_ID, "gh-1", "repo-one", false, "owner/repo-one")));
        when(githubRepositoryRepository.findById(2L)).thenReturn(Optional.of(
                GithubRepository.link(OWNER_ID, "gh-2", "repo-two", false, "owner/repo-two")));

        var result = service.listMyRepos(MEMBER_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting("repoName").containsExactlyInAnyOrder("repo-one", "repo-two");
    }

    // ---------- leaveRepo ----------

    @Test
    void leaveRepo_OWNER는_바로_나갈_수_없다() {
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, OWNER_ID)).thenReturn(Optional.of(owner()));

        assertThatThrownBy(() -> service.leaveRepo(OWNER_ID, REPO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.OWNER_CANNOT_LEAVE);

        verify(repoMemberRepository, never()).delete(any());
    }

    @Test
    void leaveRepo_MEMBER는_정상적으로_나간다() {
        RepoMember member = RepoMember.of(REPO_ID, MEMBER_ID, RepoRole.MEMBER);
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, MEMBER_ID)).thenReturn(Optional.of(member));

        service.leaveRepo(MEMBER_ID, REPO_ID);

        verify(repoMemberRepository).delete(member);
    }

    // ---------- removeMember ----------

    @Test
    void removeMember_팀장이_아니면_INSUFFICIENT_REPO_PERMISSION() {
        RepoMember member = RepoMember.of(REPO_ID, MEMBER_ID, RepoRole.MEMBER);
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.removeMember(MEMBER_ID, REPO_ID, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_REPO_PERMISSION);
    }

    @Test
    void removeMember_자기_자신이_대상이면_CANNOT_ACT_ON_SELF() {
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, OWNER_ID)).thenReturn(Optional.of(owner()));

        assertThatThrownBy(() -> service.removeMember(OWNER_ID, REPO_ID, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_ACT_ON_SELF);
    }

    @Test
    void removeMember_정상_케이스면_대상_멤버가_삭제된다() {
        RepoMember target = RepoMember.of(REPO_ID, MEMBER_ID, RepoRole.MEMBER);
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, OWNER_ID)).thenReturn(Optional.of(owner()));
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, MEMBER_ID)).thenReturn(Optional.of(target));

        service.removeMember(OWNER_ID, REPO_ID, MEMBER_ID);

        verify(repoMemberRepository).delete(target);
    }

    // ---------- transferOwnership ----------

    @Test
    void transferOwnership_팀장이_아니면_INSUFFICIENT_REPO_PERMISSION() {
        RepoMember member = RepoMember.of(REPO_ID, MEMBER_ID, RepoRole.MEMBER);
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.transferOwnership(MEMBER_ID, REPO_ID, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_REPO_PERMISSION);
    }

    @Test
    void transferOwnership_자기_자신이_대상이면_CANNOT_ACT_ON_SELF() {
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, OWNER_ID)).thenReturn(Optional.of(owner()));

        assertThatThrownBy(() -> service.transferOwnership(OWNER_ID, REPO_ID, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_ACT_ON_SELF);
    }

    @Test
    void transferOwnership_대상이_멤버가_아니면_NOT_REPO_MEMBER() {
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, OWNER_ID)).thenReturn(Optional.of(owner()));
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transferOwnership(OWNER_ID, REPO_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_REPO_MEMBER);
    }

    @Test
    void transferOwnership_정상_케이스면_역할이_서로_바뀐다() {
        RepoMember owner = owner();
        RepoMember target = RepoMember.of(REPO_ID, MEMBER_ID, RepoRole.MEMBER);
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, OWNER_ID)).thenReturn(Optional.of(owner));
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, MEMBER_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(userWithGithub(MEMBER_ID, "target-gh", "target@test.com")));

        var result = service.transferOwnership(OWNER_ID, REPO_ID, MEMBER_ID);

        assertThat(owner.getRole()).isEqualTo(RepoRole.MEMBER);
        assertThat(target.getRole()).isEqualTo(RepoRole.OWNER);
        assertThat(result.getRole()).isEqualTo("OWNER");
        assertThat(result.getGithubUsername()).isEqualTo("target-gh");
    }

    @Test
    void transferOwnership_시_GithubRepository의_연동_등록자도_새_팀장으로_갱신된다() {
        RepoMember owner = owner();
        RepoMember target = RepoMember.of(REPO_ID, MEMBER_ID, RepoRole.MEMBER);
        GithubRepository repo = GithubRepository.link(OWNER_ID, "gh-repo-1", "repo-name", false, "owner/repo-name");
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, OWNER_ID)).thenReturn(Optional.of(owner));
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, MEMBER_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(userWithGithub(MEMBER_ID, "target-gh", "target@test.com")));
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo));

        service.transferOwnership(OWNER_ID, REPO_ID, MEMBER_ID);

        assertThat(repo.getUserId()).isEqualTo(MEMBER_ID);
    }

}
