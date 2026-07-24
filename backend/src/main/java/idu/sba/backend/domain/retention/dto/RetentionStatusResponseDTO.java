package idu.sba.backend.domain.retention.dto;

import lombok.Getter;

// 리텐션 현황 (API-051) — 프론트가 streak을 읽는다. currentStreak/submittedToday도 함께 내려 호환.
@Getter
public class RetentionStatusResponseDTO {

    private final int streak;
    private final int currentStreak;
    private final boolean submittedToday;

    private RetentionStatusResponseDTO(int streak, boolean submittedToday) {
        this.streak = streak;
        this.currentStreak = streak;
        this.submittedToday = submittedToday;
    }

    public static RetentionStatusResponseDTO of(int streak, boolean submittedToday) {
        return new RetentionStatusResponseDTO(streak, submittedToday);
    }
}
