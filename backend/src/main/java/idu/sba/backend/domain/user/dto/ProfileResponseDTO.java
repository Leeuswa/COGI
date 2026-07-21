package idu.sba.backend.domain.user.dto;

import idu.sba.backend.domain.user.entity.Level;
import idu.sba.backend.domain.user.entity.Provider;
import lombok.Getter;

import java.util.List;

@Getter
public class ProfileResponseDTO {

    private final Long userId;
    private final String nickname;
    private final String githubUsername; // GitHub 연동 탭 표시용
    private final String email;
    private final Level level;
    private final List<String> interests;
    private final Long planId;
    private final boolean onboardingCompleted;   // 프론트 온보딩 가드용
    private final boolean guideConfirmed;
    private final Provider provider;             // 가입 방식 (마이페이지 표시/분기용)

    private ProfileResponseDTO(Long userId,String nickname, String email, Level level, List<String> interests,
                               Long planId, boolean onboardingCompleted, boolean guideConfirmed, Provider provider,String githubUsername) {
        this.userId = userId;
        this.nickname = nickname;
        this.email = email;
        this.level = level;
        this.interests = interests;
        this.planId = planId;
        this.onboardingCompleted = onboardingCompleted;
        this.guideConfirmed = guideConfirmed;
        this.provider = provider;
        this.githubUsername = githubUsername;
    }

    public static ProfileResponseDTO of(Long userId, String nickname, String email, Level level, List<String> interests,
                                        Long planId, boolean onboardingCompleted, boolean guideConfirmed, Provider provider,String githubUsername) {
        return new ProfileResponseDTO(userId,nickname, email, level, interests, planId, onboardingCompleted, guideConfirmed, provider,githubUsername);
    }

}
