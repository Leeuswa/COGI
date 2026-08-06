package idu.sba.backend.domain.user.service;

import idu.sba.backend.domain.payment.service.SubscriptionService;
import idu.sba.backend.domain.terms.dto.AgreedTermDTO;
import idu.sba.backend.domain.terms.dto.ReagreementResponseDTO;
import idu.sba.backend.domain.terms.dto.TermResponseDTO;
import idu.sba.backend.domain.terms.entity.Term;
import idu.sba.backend.domain.terms.entity.TermType;
import idu.sba.backend.domain.terms.entity.UserAgreement;
import idu.sba.backend.domain.terms.repository.TermRepository;
import idu.sba.backend.domain.terms.repository.UserAgreementRepository;
import idu.sba.backend.domain.repo.entity.RepoMember;
import idu.sba.backend.domain.repo.entity.RepoRole;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.repo.repository.RepoInvitationRepository;
import idu.sba.backend.domain.repo.repository.RepoMemberRepository;
import idu.sba.backend.domain.user.dto.*;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import idu.sba.backend.global.mail.HtmlMailSender;
import idu.sba.backend.global.security.TotpService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService{

    private final UserRepository userRepository;
    private final UserAgreementRepository userAgreementRepository;
    private final TermRepository termRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final HtmlMailSender htmlMailSender;
    private final SubscriptionService subscriptionService;
    private final RepoMemberRepository repoMemberRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final RepoInvitationRepository repoInvitationRepository;

    // 소셜·로컬 공통 탈퇴 확인 문구 — 이 값과 정확히 일치해야 탈퇴 진행
    private static final String WITHDRAW_PHRASE = "탈퇴하겠습니다";


    //내가 동의한 약관 id 목록 (마이페이지 동의 현황)
    @Override
    @Transactional(readOnly = true)
    public List<Long> getAgreedTermIds(Long userId) {
        return userAgreementRepository.findByUserIdAndAgreedTrue(userId).stream()
                .map(UserAgreement::getTermId)
                .toList();
    }

    //동의 약관 + 동의 당시 버전 (마이페이지 개정 배지·재동의 판정)
    @Override
    @Transactional(readOnly = true)
    public List<AgreedTermDTO> getAgreementVersions(Long userId) {
        return userAgreementRepository.findByUserIdAndAgreedTrue(userId).stream()
                .map(AgreedTermDTO::from)
                .toList();
    }

    // 재동의 판정 — 필수 약관(PAYMENT 제외) 중 내 동의 버전이 현재 버전과 다르거나(미동의 포함) 하나라도 있으면 대상.
    // agreedVersion 이 null 인 레거시 동의도 대상으로 본다(backfill SQL 로 방지 가능).
    @Override
    @Transactional(readOnly = true)
    public ReagreementResponseDTO checkReagreement(Long userId) {
        // 관리자 계정은 재동의 게이트 제외 (약관 관리 주체이므로 스스로 재동의 받을 필요 없음)
        if (findUser(userId).getRole() == idu.sba.backend.domain.user.entity.Role.ADMIN) {
            return new ReagreementResponseDTO(List.of());
        }
        // Collectors.toMap 은 null 값을 못 담는다(레거시 동의는 agreedVersion=null) → 수동 put 으로 null 스킵.
        // 키가 없으면 get()==null 이라 재동의 대상 판정 결과는 동일하다.
        Map<Long, String> agreedVer = new java.util.HashMap<>();
        for (UserAgreement a : userAgreementRepository.findByUserIdAndAgreedTrue(userId)) {
            if (a.getAgreedVersion() != null) agreedVer.put(a.getTermId(), a.getAgreedVersion());
        }

        List<TermResponseDTO> outdated = termRepository.findByIsRequiredTrue().stream()
                .filter(t -> t.getType() != TermType.PAYMENT)
                .filter(t -> !java.util.Objects.equals(agreedVer.get(t.getId()), t.getVersion()))
                .map(TermResponseDTO::from)
                .toList();

        return new ReagreementResponseDTO(outdated);
    }

    //프로필 조회
    @Override
    @Transactional(readOnly = true)
    public ProfileResponseDTO getProfile(Long userId) {
        User user = findUser(userId);
        return ProfileResponseDTO.of(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getLevel(),
                toList(user.getInterests()),
                user.getPlanId(),
                subscriptionService.getCurrentPlanEntity(userId).getName(),
            Boolean.TRUE.equals(user.getOnboardingCompleted()),
                Boolean.TRUE.equals(user.getGuideConfirmed()),
                user.getProvider(),
                user.getGithubUsername(),
                user.getRole(),
                Boolean.TRUE.equals(user.getTotpEnabled()));
    }

    //프로필 수정
    @Override
    @Transactional
    public void updateProfile(Long userId,ProfileUpdateDTO req) {
        User user = findUser(userId);
        user.updateProfile(req.getNickname(),req.getLevel(),toCsv(req.getInterests()));

    }

    //온보딩 상태 조회
    @Override
    @Transactional(readOnly = true)
    public OnboardingStatusResponseDTO getOnboardingStatus(Long userId) {
        User user = findUser(userId);
        return OnboardingStatusResponseDTO.of(
                Boolean.TRUE.equals(user.getOnboardingCompleted()),
                Boolean.TRUE.equals(user.getGuideConfirmed()));
    }

    //온보딩 제출
    @Override
    @Transactional
    public void submitOnboarding(Long userId, OnboardingRequestDTO req) {
        User user = findUser(userId);
        user.completeOnboarding(req.getLevel(), toCsv(req.getInterests()),req.isGuideConfirmed() );

    }

    //로그인 했을때 비밀번호 변경
    @Override
    @Transactional
    public void changePassword(Long userId, PasswordChangeDTO req) {

        if(!req.getNewPassword().equals(req.getNewPasswordConfirm()))
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);

        User user = findUser(userId);

        if(user.getPassword() == null)
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);

        // 현재 비밀번호 검증
        if(!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword()))
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        // 새 비밀번호가 기존과 같으면 거부
        if(passwordEncoder.matches(req.getNewPassword(), user.getPassword()))
            throw new BusinessException(ErrorCode.SAME_AS_OLD_PASSWORD);
        user.changePassword(passwordEncoder.encode(req.getNewPassword()));

        //변경 메일 알림
        try {
                sendPasswordChangedMail(user.getEmail());
        } catch (Exception e){
            // 메일 실패는 무시
        }
    }

    private void sendPasswordChangedMail(String to){
        if(to == null) return;
        String inner = """
        <p style="margin:0 0 8px;color:#1b2a4a;font-size:17px;font-weight:bold;">비밀번호가 변경되었습니다</p>
        <p style="margin:0 0 16px;color:#40507a;font-size:13px;line-height:1.7;">회원님의 비밀번호가 방금 변경되었습니다.</p>
        <div style="background:#fff;border:3px solid #ff6b57;padding:14px;color:#1b2a4a;font-size:12.5px;line-height:1.7;">
             본인이 변경하지 않았다면 즉시 <b>비밀번호 찾기</b>로 재설정해주세요.
        </div>
        """;
        htmlMailSender.send(to, "[COGI] 비밀번호가 변경되었습니다", inner);
    }

    @Override
    @Transactional
    public TotpSetupResponseDTO setupTotp(Long userId) {
        User user = findUser(userId); //유저 조회
        // 이미 켜져 있으면 재설정 불가 — 바꾸려면 먼저 해제해야 한다
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BusinessException(ErrorCode.TOTP_ALREADY_ENABLED);
        }
        String secret = totpService.generateSecret(); //새 시크릿 발급
        user.prepareTotp(secret);
        // 시크릿 + 인증앱 등록용 URI 반환
        return new TotpSetupResponseDTO(secret, totpService.qrDataUri(secret, user.getEmail()));
    }

    // 2차 인증 해제 — 켜져 있을 때만 의미 있음
    @Override
    @Transactional
    public void disableTotp(Long userId) {
        findUser(userId).disableTotp();
    }


    @Override
    @Transactional
    public void enableTotp(Long userId, TotpEnableRequestDTO req) {
        User user = findUser(userId);

        // setup 없이 바로 enable 시도 → 순서 오류
        if(user.getTotpSecret() == null){
            throw new BusinessException(ErrorCode.TOTP_NOT_INITIATED);
        }
        // 입력 코드가 시크릿과 안 맞으면 거부
        if(!totpService.verify(user.getTotpSecret(),req.getCode())){
            throw new BusinessException(ErrorCode.TOTP_CODE_INVALID);
        }
        user.enableTotp(); // 검증 통과 → 활성화
    }

    // 약관 동의 저장 (온보딩 최초 동의 + 개정 후 재동의 공용).
    // 기존 행이 있으면 버전을 최신으로 갱신(재동의), 없으면 현재 버전으로 새로 저장.
    @Override
    @Transactional
    public void submitAgreements(Long userId, AgreementSubmitDTO req) {
        for (AgreementSubmitDTO.Item item : req.getAgreements()) {
            Term term = termRepository.findById(item.getTermId()).orElse(null);
            if (term == null) continue;                                 // 없는 약관 id 무시

            if (Boolean.TRUE.equals(item.getAgreed())) {
                // 동의(신규/재동의) — 버전·일시 갱신
                userAgreementRepository.findFirstByUserIdAndTermIdOrderByIdAsc(userId, term.getId())
                        .ifPresentOrElse(
                                a -> a.reagree(term.getVersion()),
                                () -> userAgreementRepository.save(UserAgreement.of(userId, term.getId(), term.getVersion()))
                        );
            } else {
                // 동의 철회 — 필수 약관은 철회 불가(무시), 선택 약관만 agreed=false
                if (Boolean.TRUE.equals(term.getIsRequired())) continue;
                userAgreementRepository.findFirstByUserIdAndTermIdOrderByIdAsc(userId, term.getId())
                        .ifPresent(UserAgreement::withdraw);
            }
        }
    }

    // 회원탈퇴 — 문구 재검증 → 내가 팀장인 팀마다 위임/해체 → 소프트삭제·익명화.
    // 소셜/로컬 모두 동일하게 문구만으로 인증한다(비번 분기 없음).
    @Override
    @Transactional
    public void withdraw(Long userId, WithdrawRequestDTO req) {
        User user = findUser(userId);

        // 프론트만 믿지 않고 서버에서 문구 재검증
        if (req == null || !WITHDRAW_PHRASE.equals(req.getConfirm() == null ? null : req.getConfirm().trim())) {
            throw new BusinessException(ErrorCode.INVALID_WITHDRAW_CONFIRM);
        }

        // 내가 OWNER인 레포마다 처리 (여러 팀장 가능)
        for (RepoMember myMembership : repoMemberRepository.findByUserId(userId)) {
            if (myMembership.getRole() != RepoRole.OWNER) continue; // 팀원인 팀은 그대로 둔다(데이터 보존)
            Long repoId = myMembership.getRepoId();

            // 나 빼고 가장 먼저 합류한 팀원 = 후임 팀장
            Optional<RepoMember> heir = repoMemberRepository.findByRepoId(repoId).stream()
                    .filter(m -> !m.getUserId().equals(userId))
                    .min(Comparator.comparing(RepoMember::getJoinedAt));

            if (heir.isPresent()) {
                RepoMember next = heir.get();
                myMembership.changeRole(RepoRole.MEMBER); // 나는 강등(곧 탈퇴 표시로 남음)
                next.changeRole(RepoRole.OWNER);
                githubRepositoryRepository.findById(repoId)
                        .ifPresent(repo -> repo.changeOwner(next.getUserId())); // 연동 등록자도 갱신
            } else {
                // 팀원 0명 → 팀 해체: 내 멤버십 + 대기 초대 + 레포 연동 자체를 삭제.
                // repositories row를 남겨두면 existsByGithubRepoId가 계속 true라서 아무도 접근 못 하는
                // 이 GitHub 레포를 COGI에서 영원히 재연동할 수 없게 되는 문제가 있었음 — PR/리뷰 이력은
                // repoId만 남은 채(조회 불가) 그대로 보존되고, 실제로 그 레포에 대한 접근 권한이 없던 것과
                // 동일하므로 문제 없음
                repoMemberRepository.delete(myMembership);
                repoInvitationRepository.deleteByRepoId(repoId);
                githubRepositoryRepository.deleteById(repoId);
            }
        }

        user.withdraw();
    }

    private User findUser(Long userId){
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }


    private String toCsv(List<String> interests){
        if(interests == null || interests.isEmpty()) return null;
        return String.join(",",interests);
    }

    private List<String> toList(String interests){
        if(interests == null || interests.isBlank()) return List.of();
        return List.of(interests.split(","));
    }




}
