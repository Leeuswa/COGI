package idu.sba.backend.domain.user.dto;

import idu.sba.backend.domain.user.entity.Level;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

@Getter
public class OnboardingRequestDTO {

    @NotNull(message = "수준을 선택해주세요.")
    private Level level;

    private List<String> interests;

    private boolean guideConfirmed;   // 가이드 확인 여부
}