package idu.sba.backend.domain.user.dto;

import lombok.Getter;

@Getter
public class OnboardingStatusResponseDTO {

    private final boolean completed;
    private final boolean guideConfirmed;

    private OnboardingStatusResponseDTO(boolean completed, boolean guideConfirmed) {
        this.completed = completed;
        this.guideConfirmed = guideConfirmed;
    }


    public static OnboardingStatusResponseDTO of(boolean completed, boolean guideConfirmed){
        return new OnboardingStatusResponseDTO(completed,guideConfirmed);
    }
}
