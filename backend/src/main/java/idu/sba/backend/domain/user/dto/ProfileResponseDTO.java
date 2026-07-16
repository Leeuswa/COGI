package idu.sba.backend.domain.user.dto;

import idu.sba.backend.domain.user.entity.Level;
import lombok.Getter;

import java.util.List;

@Getter
public class ProfileResponseDTO {

    private final String nickname;
    private final String email;
    private final Level level;
    private final List<String> interests;
    private final Long planId;

    private ProfileResponseDTO(String nickname, String email, Level level,
                               List<String> interests, Long planId) {
        this.nickname = nickname;
        this.email = email;
        this.level = level;
        this.interests = interests;
        this.planId = planId;
    }

    public static ProfileResponseDTO of(String nickname, String email, Level level,
                                        List<String> interests, Long planId) {
        return new ProfileResponseDTO(nickname, email, level, interests, planId);
    }

}
