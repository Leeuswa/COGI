package idu.sba.backend.domain.auth.service;

import idu.sba.backend.domain.auth.dto.*;
import idu.sba.backend.domain.auth.entity.PasswordResetToken;

public interface AuthService {

    //인증코드 발송
    void sendCode(EmailSendCodeRequestDTO request);

    //인증코드 검증
    void verifyCode(EmailVerifyRequestDTO request);

    //회원가입
    Long signup(SignupRequestDTO request);

    //로그인
    TokenResponseDTO login(LoginRequestDTO request);

    //메일인증 코드 확인 후 resetToken 발급
    String resetRequest(PasswordResetRequestDTO request);

    //비밀번호 재설정 (비밀번호 5회이상 틀려서 계정 잠금일 시)
    void resetPassword(PasswordResetDTO request);

    //토큰 연장
    String refreshToken(Long userId);
}
