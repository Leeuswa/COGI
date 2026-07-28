package idu.sba.backend.domain.user.service;

import idu.sba.backend.domain.payment.service.SubscriptionService;
import idu.sba.backend.domain.repo.entity.GithubRepository;
import idu.sba.backend.domain.repo.entity.RepoMember;
import idu.sba.backend.domain.repo.entity.RepoRole;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.repo.repository.RepoInvitationRepository;
import idu.sba.backend.domain.repo.repository.RepoMemberRepository;
import idu.sba.backend.domain.terms.repository.TermRepository;
import idu.sba.backend.domain.terms.repository.UserAgreementRepository;
import idu.sba.backend.domain.user.dto.WithdrawRequestDTO;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.entity.UserStatus;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import idu.sba.backend.global.mail.HtmlMailSender;
import idu.sba.backend.global.security.TotpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplWithdrawTest {

    @Mock private UserRepository userRepository;
    @Mock private UserAgreementRepository userAgreementRepository;
    @Mock private TermRepository termRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TotpService totpService;
    @Mock private HtmlMailSender htmlMailSender;
    @Mock private SubscriptionService subscriptionService;
    @Mock private RepoMemberRepository repoMemberRepository;
    @Mock private GithubRepositoryRepository githubRepositoryRepository;
    @Mock private RepoInvitationRepository repoInvitationRepository;

    @InjectMocks private UserServiceImpl service;

    private static final Long OWNER_ID = 10L, EARLY_ID = 20L, LATE_ID = 30L, REPO_ID = 1L;
    private static final String PHRASE = "탈퇴하겠습니다";

    private User owner() {
        User u = User.builder().email("owner@x.com").password("p").nickname("팀장").build();
        ReflectionTestUtils.setField(u, "id", OWNER_ID);
        return u;
    }

    private RepoMember member(Long userId, RepoRole role, LocalDateTime joinedAt) {
        RepoMember m = RepoMember.of(REPO_ID, userId, role);
        ReflectionTestUtils.setField(m, "joinedAt", joinedAt);
        return m;
    }

    @Test
    void withdraw_rejectedWhenConfirmPhraseWrong() {
        User u = owner();
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(u));
        WithdrawRequestDTO req = new WithdrawRequestDTO();
        ReflectionTestUtils.setField(req, "confirm", "탈퇴할래요"); // 틀린 문구

        assertThatThrownBy(() -> service.withdraw(OWNER_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_WITHDRAW_CONFIRM);

        assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE); // 상태 안 바뀜
        verify(repoMemberRepository, never()).findByUserId(any());
    }

    @Test
    void withdraw_ownerDelegatesToEarliestMember() {
        User u = owner();
        RepoMember ownerM = member(OWNER_ID, RepoRole.OWNER, LocalDateTime.of(2024, 1, 1, 0, 0));
        RepoMember early = member(EARLY_ID, RepoRole.MEMBER, LocalDateTime.of(2024, 2, 1, 0, 0));
        RepoMember late = member(LATE_ID, RepoRole.MEMBER, LocalDateTime.of(2024, 6, 1, 0, 0));
        GithubRepository repo = GithubRepository.link(OWNER_ID, "gh1", "cogi", false, "team/cogi");

        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(u));
        when(repoMemberRepository.findByUserId(OWNER_ID)).thenReturn(List.of(ownerM));
        when(repoMemberRepository.findByRepoId(REPO_ID)).thenReturn(List.of(ownerM, late, early));
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo));

        WithdrawRequestDTO req = new WithdrawRequestDTO();
        ReflectionTestUtils.setField(req, "confirm", PHRASE);
        service.withdraw(OWNER_ID, req);

        assertThat(early.getRole()).isEqualTo(RepoRole.OWNER);   // 가장 먼저 들어온 팀원이 팀장
        assertThat(late.getRole()).isEqualTo(RepoRole.MEMBER);   // 늦게 온 팀원은 그대로
        assertThat(ownerM.getRole()).isEqualTo(RepoRole.MEMBER); // 기존 팀장은 강등
        assertThat(repo.getUserId()).isEqualTo(EARLY_ID);        // 연동 등록자도 갱신
        // 익명화 확인
        assertThat(u.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(u.getNickname()).isEqualTo("탈퇴한 회원");
        assertThat(u.getEmail()).isEqualTo("withdrawn_10@deleted.local");
    }

    @Test
    void withdraw_soloOwnerDisbandsTeam() {
        User u = owner();
        RepoMember ownerM = member(OWNER_ID, RepoRole.OWNER, LocalDateTime.of(2024, 1, 1, 0, 0));

        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(u));
        when(repoMemberRepository.findByUserId(OWNER_ID)).thenReturn(List.of(ownerM));
        when(repoMemberRepository.findByRepoId(REPO_ID)).thenReturn(List.of(ownerM)); // 나 혼자

        WithdrawRequestDTO req = new WithdrawRequestDTO();
        ReflectionTestUtils.setField(req, "confirm", PHRASE);
        service.withdraw(OWNER_ID, req);

        verify(repoMemberRepository).delete(ownerM);            // 내 멤버십 삭제(팀 해체)
        verify(repoInvitationRepository).deleteByRepoId(REPO_ID); // 대기 초대 정리
        verify(githubRepositoryRepository, never()).findById(any()); // 위임 없음
        assertThat(u.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
    }

    @Test
    void withdraw_plainMemberJustAnonymized() {
        User u = owner();
        RepoMember justMember = member(OWNER_ID, RepoRole.MEMBER, LocalDateTime.of(2024, 1, 1, 0, 0));

        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(u));
        when(repoMemberRepository.findByUserId(OWNER_ID)).thenReturn(List.of(justMember));

        WithdrawRequestDTO req = new WithdrawRequestDTO();
        ReflectionTestUtils.setField(req, "confirm", PHRASE);
        service.withdraw(OWNER_ID, req);

        verify(repoMemberRepository, never()).delete(any());
        verify(repoMemberRepository, never()).findByRepoId(any()); // OWNER가 아니라 위임 스킵
        assertThat(u.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(u.getNickname()).isEqualTo("탈퇴한 회원");
    }
}
