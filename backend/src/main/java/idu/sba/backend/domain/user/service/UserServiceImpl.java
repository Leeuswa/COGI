package idu.sba.backend.domain.user.service;

import idu.sba.backend.domain.user.dto.OnboardingRequestDTO;
import idu.sba.backend.domain.user.dto.OnboardingStatusResponseDTO;
import idu.sba.backend.domain.user.dto.ProfileResponseDTO;
import idu.sba.backend.domain.user.dto.ProfileUpdateDTO;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService{

    private final UserRepository userRepository;

    //프로필 조회
    @Override
    @Transactional(readOnly = true)
    public ProfileResponseDTO getProfile(Long userId) {
        User user = findUser(userId);
        return ProfileResponseDTO.of(
                user.getNickname(),
                user.getEmail(),
                user.getLevel(),
                toList(user.getInterests()),
                user.getPlanId(),
            Boolean.TRUE.equals(user.getOnboardingCompleted()),
                Boolean.TRUE.equals(user.getGuideConfirmed()),
                user.getProvider(),
                user.getGithubUsername());
    }

    //프로필 수정
    @Override
    @Transactional
    public void updateProfile(Long userId,ProfileUpdateDTO req) {
        User user = findUser(userId);
        user.updateProfile(req.getNickname(),req.getLevel(),toCsv(req.getInterests()));

    }

    //온보딩 상태 조회
    @Override
    @Transactional(readOnly = true)
    public OnboardingStatusResponseDTO getOnboardingStatus(Long userId) {
        User user = findUser(userId);
        return OnboardingStatusResponseDTO.of(
                Boolean.TRUE.equals(user.getOnboardingCompleted()),
                Boolean.TRUE.equals(user.getGuideConfirmed()));
    }

    //온보딩 제출
    @Override
    @Transactional
    public void submitOnboarding(Long userId, OnboardingRequestDTO req) {
        User user = findUser(userId);
        user.completeOnboarding(req.getLevel(), toCsv(req.getInterests()),req.isGuideConfirmed() );

    }


    private User findUser(Long userId){
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }


    private String toCsv(List<String> interests){
        if(interests == null || interests.isEmpty()) return null;
        return String.join(",",interests);
    }

    private List<String> toList(String interests){
        if(interests == null || interests.isBlank()) return List.of();
        return List.of(interests.split(","));
    }



}
