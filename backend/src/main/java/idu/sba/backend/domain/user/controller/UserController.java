package idu.sba.backend.domain.user.controller;

import idu.sba.backend.domain.user.dto.*;
import idu.sba.backend.domain.user.service.UserService;
import idu.sba.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    //  프로필 조회
    @GetMapping("/profile")
    public ApiResponse<ProfileResponseDTO> getProfile(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.getProfile(userId));
    }

    // 내가 동의한 약관 id 목록 (마이페이지 동의 현황)
    @GetMapping("/agreements")
    public ApiResponse<java.util.List<Long>> getAgreedTermIds(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.getAgreedTermIds(userId));
    }

    // 프로필 수정
    @PatchMapping("/profile")
    public ApiResponse<Void> updateProfile(@AuthenticationPrincipal Long userId,
                                           @Valid @RequestBody ProfileUpdateDTO request) {
        userService.updateProfile(userId, request);
        return ApiResponse.ok("프로필이 수정되었습니다.");
    }

    // 온보딩 상태 조회
    @GetMapping("/onboarding-status")
    public ApiResponse<OnboardingStatusResponseDTO> getOnboardingStatus(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.getOnboardingStatus(userId));
    }

    //온보딩 제출
    @PostMapping("/onboarding")
    public ApiResponse<Void> submitOnboarding(@AuthenticationPrincipal Long userId,
                                              @Valid @RequestBody OnboardingRequestDTO request) {
        userService.submitOnboarding(userId, request);
        return ApiResponse.ok("온보딩이 완료되었습니다.");
    }

    //로그인 시 비밀번호 변경하는 API
    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal Long userId,
                                            @Valid @RequestBody PasswordChangeDTO request){
        userService.changePassword(userId,request);
        return ApiResponse.ok("비밀번호가 변경되었습니다.");
    }

    // 시크릿 발급 + 인증앱 등록용 QR/URI 반환 (아직 활성화 아님)
    @PostMapping("/totp/setup")
    public ApiResponse<TotpSetupResponseDTO> setupTotp(@AuthenticationPrincipal Long userId){
        return ApiResponse.ok(userService.setupTotp(userId));
    }

    // 앱에 뜬 6자리 검증 통과 시 2차 인증 활성화
    @PostMapping("/totp/enable")
    public ApiResponse<Void> enableTotp(@AuthenticationPrincipal Long userId,
                                        @Valid @RequestBody TotpEnableRequestDTO request){
        userService.enableTotp(userId,request);
        return ApiResponse.ok("2차 인증이 활성화 되었습니다.");
    }
}