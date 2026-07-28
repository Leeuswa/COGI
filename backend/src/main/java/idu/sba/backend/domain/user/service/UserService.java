package idu.sba.backend.domain.user.service;

import idu.sba.backend.domain.terms.dto.AgreedTermDTO;
import idu.sba.backend.domain.terms.dto.ReagreementResponseDTO;
import idu.sba.backend.domain.user.dto.*;

import java.util.List;

public interface UserService {

    //내가 동의한 약관 id 목록 (마이페이지 동의 현황)
    List<Long> getAgreedTermIds(Long userId);

    //내가 동의한 약관 + 동의 당시 버전 (마이페이지에서 개정 여부 판정)
    List<AgreedTermDTO> getAgreementVersions(Long userId);

    //약관 개정으로 재동의가 필요한지 (필수 약관, PAYMENT 제외)
    ReagreementResponseDTO checkReagreement(Long userId);

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

    TotpSetupResponseDTO setupTotp(Long userId);

    //2단계 인증
    void enableTotp(Long userId,TotpEnableRequestDTO req);

    //소셜로그인 약관동의
    void submitAgreements(Long userId, AgreementSubmitDTO req);
}
