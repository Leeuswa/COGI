package idu.sba.backend.domain.auth.controller;

import idu.sba.backend.domain.auth.dto.*;
import idu.sba.backend.domain.auth.service.AuthService;
import idu.sba.backend.global.common.ApiResponse;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import idu.sba.backend.global.security.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

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
    public ApiResponse<Void> login(
            @Valid @RequestBody LoginRequestDTO request,
            HttpServletResponse response) {
        TokenResponseDTO token = authService.login(request);
        // 토큰을 HttpOnly 쿠키에 담아 응답 헤더에 추가
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.createAccessTokenCookie(token.getAccessToken()).toString());
        return ApiResponse.ok("로그인 성공");
    }

    //이메일 인증코드 확인 후 resetToken발급
    @PostMapping("/password/reset-request")
    public ApiResponse<Map<String, String>> resetRequest(
            @Valid @RequestBody PasswordResetRequestDTO request) {
        String resetToken = authService.resetRequest(request);
        return ApiResponse.ok("인증되었습니다.", Map.of("resetToken", resetToken));
    }

    //비밀번호 재설정
    @PatchMapping("/password/reset")
    public ApiResponse<Void> resetPassword(
            @Valid @RequestBody PasswordResetDTO request) {
        authService.resetPassword(request);
        return ApiResponse.ok("비밀번호가 재설정되었습니다.");
    }


    //로그아웃 - 쿠기삭제
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response){
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.deleteAccessTokenCookie().toString());
        return ApiResponse.ok("로그아웃 되었습니다.");
    }

    @PostMapping("/refresh")
    public ApiResponse<Void> refresh(@AuthenticationPrincipal Long userId, HttpServletResponse response) {
        if (userId == null) throw new BusinessException(ErrorCode.LOGIN_FAILED);   // 만료/미인증 → 재로그인
        String token = authService.refreshToken(userId);
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.createAccessTokenCookie(token).toString());
        return ApiResponse.ok("세션이 연장되었습니다.");
    }


}
