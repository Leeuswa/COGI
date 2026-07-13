package idu.sba.backend.domain.auth.service;

import idu.sba.backend.domain.auth.dto.*;

public interface AuthService {

    //인증코드 발송
    void sendCode(EmailSendCodeRequestDto request);

    //인증코드 검증
    void verifyCode(EmailVerifyRequestDto request);

    //회원가입
    Long signup(SignupRequestDto request);

    //로그인
    TokenResponseDto login(LoginRequestDTO request);
}
