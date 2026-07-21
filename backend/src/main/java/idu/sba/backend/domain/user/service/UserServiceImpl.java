package idu.sba.backend.domain.user.service;

import idu.sba.backend.domain.terms.entity.UserAgreement;
import idu.sba.backend.domain.terms.repository.UserAgreementRepository;
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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService{

    private final UserRepository userRepository;
    private final UserAgreementRepository userAgreementRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final HtmlMailSender htmlMailSender;


    //내가 동의한 약관 id 목록 (마이페이지 동의 현황)
    @Override
    @Transactional(readOnly = true)
    public List<Long> getAgreedTermIds(Long userId) {
        return userAgreementRepository.findByUserIdAndAgreedTrue(userId).stream()
                .map(UserAgreement::getTermId)
                .toList();
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
            Boolean.TRUE.equals(user.getOnboardingCompleted()),
                Boolean.TRUE.equals(user.getGuideConfirmed()),
                user.getProvider(),
                user.getGithubUsername(),
                user.getRole());
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

        if(!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword()))
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
        String secret = totpService.generateSecret(); //새 시크릿 발급
        user.prepareTotp(secret);
        // 시크릿 + 인증앱 등록용 URI 반환
        return new TotpSetupResponseDTO(secret, totpService.qrDataUri(secret, user.getEmail()));
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

    @Override
    @Transactional
    public void submitAgreements(Long userId, AgreementSubmitDTO req) {
        // 이미 동의한 약관 id → 중복 insert 방지
        Set<Long> already = userAgreementRepository.findByUserIdAndAgreedTrue(userId).stream()
                .map(UserAgreement::getTermId)
                .collect(Collectors.toSet());

        List<UserAgreement> toSave = req.getAgreements().stream()
                .filter(a -> Boolean.TRUE.equals(a.getAgreed()))   // 동의한 것만
                .map(AgreementSubmitDTO.Item::getTermId)
                .filter(termId -> !already.contains(termId))       // 이미 있으면 스킵
                .map(termId -> UserAgreement.of(userId, termId))
                .toList();

        userAgreementRepository.saveAll(toSave);
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
