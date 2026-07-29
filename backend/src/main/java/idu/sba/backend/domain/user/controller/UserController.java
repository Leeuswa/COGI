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

    // 내 동의 약관 + 동의 당시 버전 (마이페이지 개정 배지/선택약관 재동의용)
    @GetMapping("/agreements/versions")
    public ApiResponse<java.util.List<idu.sba.backend.domain.terms.dto.AgreedTermDTO>> getAgreementVersions(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.getAgreementVersions(userId));
    }

    // FR-91 약관 재동의 필요 여부 — 로그인 후 대시보드 진입 전 게이트가 조회
    @GetMapping("/terms/reagreement")
    public ApiResponse<idu.sba.backend.domain.terms.dto.ReagreementResponseDTO> checkReagreement(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.checkReagreement(userId));
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

    // 2차 인증 해제
    @PostMapping("/totp/disable")
    public ApiResponse<Void> disableTotp(@AuthenticationPrincipal Long userId){
        userService.disableTotp(userId);
        return ApiResponse.ok("2차 인증이 해제되었습니다.");
    }

    @PostMapping("/agreements")
    public ApiResponse<Void> submitAgreements(@AuthenticationPrincipal Long userId,
                                              @RequestBody AgreementSubmitDTO request) {
        userService.submitAgreements(userId, request);
        return ApiResponse.ok("약관 동의가 저장되었습니다.");
    }

    // 회원탈퇴 — 팀장이면 첫 팀원에게 위임(없으면 팀 해체) 후 소프트삭제·익명화
    @DeleteMapping
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal Long userId,
                                      @Valid @RequestBody WithdrawRequestDTO request) {
        userService.withdraw(userId, request);
        return ApiResponse.ok("회원 탈퇴가 완료되었습니다.");
    }

}