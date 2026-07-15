package idu.sba.backend.domain.auth.controller;

import idu.sba.backend.domain.auth.dto.*;
import idu.sba.backend.domain.auth.service.AuthService;
import idu.sba.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    //이메일 인증코드 확인 후 resetToken발급
    // 반환타입 ApiResponse<Map<String,String>> 인 이유:
    //   프론트에 돌려줄 데이터(resetToken)가 있음. 근데 필드가 1개뿐이라
    //   응답 DTO 클래스까지 만들지 않고 Map 으로 간단히 담는다. (필드 늘면 DTO로 승격)
    @PostMapping("/password/reset-request")
    public ApiResponse<Map<String, String>> resetRequest(
            @Valid @RequestBody PasswordResetRequestDTO request) {
        String resetToken = authService.resetRequest(request);
        return ApiResponse.ok("인증되었습니다.", Map.of("resetToken", resetToken));
    }

    //비밀번호 재설정
   // 반환타입 ApiResponse<Void> 인 이유:
    //   비밀번호만 바꾸면 끝이라 프론트에 돌려줄 데이터가 없음 → data 없이 메시지만
    @PatchMapping("/password/reset")
    public ApiResponse<Void> resetPassword(
            @Valid @RequestBody PasswordResetDTO request) {
        authService.resetPassword(request);
        return ApiResponse.ok("비밀번호가 재설정되었습니다.");
    }
}
