package idu.sba.backend.domain.user.service;

import idu.sba.backend.domain.user.dto.*;

public interface UserService {

    //프로필 조회
    ProfileResponseDTO getProfile(Long userId);
    //프로필 수정
    void updateProfile(Long userId,ProfileUpdateDTO request);
    //온보딩 확인여부
    OnboardingStatusResponseDTO getOnboardingStatus(Long userId);
    //온보딩 제출 여부
    void submitOnboarding(Long userId, OnboardingRequestDTO request);

    //로그인 했을때 비밀번호 변경
    void changePassword(Long userId, PasswordChangeDTO req);


}
