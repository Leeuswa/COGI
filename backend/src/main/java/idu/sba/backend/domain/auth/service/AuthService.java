package idu.sba.backend.domain.auth.service;

import idu.sba.backend.domain.auth.dto.*;

public interface AuthService {

    //인증코드 발송
    void sendCode(EmailSendCodeRequestDTO request);

    //인증코드 검증
    void verifyCode(EmailVerifyRequestDTO request);

    //회원가입
    Long signup(SignupRequestDTO request);

    //로그인
    TokenResponseDTO login(LoginRequestDTO request);
}
