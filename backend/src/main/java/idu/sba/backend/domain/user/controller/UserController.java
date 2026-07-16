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
}