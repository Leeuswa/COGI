package idu.sba.backend.domain.retention.dto;

import lombok.Getter;

import java.util.List;

// 리텐션 현황 (API-051) — 프론트가 streak을 읽는다. currentStreak/submittedToday도 함께 내려 호환.
// submitDays는 달력에 점 찍을 날짜(yyyy-MM-dd). 예전엔 브라우저에만 있어서 DB를 비워도 안 지워졌다
@Getter
public class RetentionStatusResponseDTO {

    private final int streak;
    private final int currentStreak;
    private final boolean submittedToday;
    private final List<String> submitDays;

    private RetentionStatusResponseDTO(int streak, boolean submittedToday, List<String> submitDays) {
        this.streak = streak;
        this.currentStreak = streak;
        this.submittedToday = submittedToday;
        this.submitDays = submitDays;
    }

    public static RetentionStatusResponseDTO of(int streak, boolean submittedToday, List<String> submitDays) {
        return new RetentionStatusResponseDTO(streak, submittedToday, submitDays);
    }
}
