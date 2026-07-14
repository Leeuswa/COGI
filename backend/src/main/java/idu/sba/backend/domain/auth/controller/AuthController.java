package idu.sba.backend.domain.auth.controller;

import idu.sba.backend.domain.auth.dto.*;
import idu.sba.backend.domain.auth.service.AuthService;
import idu.sba.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    //인증코드 발송
    @PostMapping("/email/send-code")
    public ApiResponse<Void> sendCode(
            @Valid @RequestBody EmailSendCodeRequestDTO request){
        authService.sendCode(request);
        return ApiResponse.ok("인증코드 발송했습니다."); //성공 응답
    }

    //인증코드 검증
    @PostMapping("/email/verify-code")
    public ApiResponse<Void> verifyCode(
            @Valid @RequestBody EmailVerifyRequestDTO request){
        authService.verifyCode(request);
        return ApiResponse.ok("이메일 인증이 완료되었습니다.");
    }

    //회원가입
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(
            @Valid @RequestBody SignupRequestDTO request){
        authService.signup(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("회원가입이 완료되었습니다."));
    }

    //로그인
    @PostMapping("/login")
    public ApiResponse<TokenResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO request) {
        TokenResponseDTO token = authService.login(request);
        return ApiResponse.ok("로그인 성공",token);  //data에 토큰 담아서 응답
    }

}
