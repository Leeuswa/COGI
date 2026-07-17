package idu.sba.backend.domain.user.service;

import idu.sba.backend.domain.user.dto.*;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import idu.sba.backend.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService{

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    @Value("${spring.mail.username}") private String mailFrom;

    //프로필 조회
    @Override
    @Transactional(readOnly = true)
    public ProfileResponseDTO getProfile(Long userId) {
        User user = findUser(userId);
        return ProfileResponseDTO.of(
                user.getNickname(),
                user.getEmail(),
                user.getLevel(),
                toList(user.getInterests()),
                user.getPlanId(),
            Boolean.TRUE.equals(user.getOnboardingCompleted()),
                Boolean.TRUE.equals(user.getGuideConfirmed()),
                user.getProvider(),
                user.getGithubUsername());
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
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to); // 받는 사람
        message.setSubject("[COGI] 비밀번호가 변경되었습니다"); //제목
        message.setText("회원님의 비밀번호가 방금 변경되었습니다.\n"
                + "본인이 변경하지 않았다면 즉시 '비밀번호 찾기'로 재설정해주세요.");  //본문
        mailSender.send(message);  //메일 발송
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
