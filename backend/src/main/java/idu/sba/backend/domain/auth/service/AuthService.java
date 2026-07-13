package idu.sba.backend.domain.auth.service;

import idu.sba.backend.domain.auth.dto.EmailSendCodeRequestDto;
import idu.sba.backend.domain.auth.dto.EmailVerifyRequestDto;
import idu.sba.backend.domain.auth.dto.SignupRequestDto;

public interface AuthService {

    //인증코드 발송
    void sendCode(EmailSendCodeRequestDto request);

    //인증코드 검증
    void verifyCode(EmailVerifyRequestDto request);

    //회원가입
    Long signup(SignupRequestDto request);
}
