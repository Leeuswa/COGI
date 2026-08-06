package idu.sba.backend.domain.retention.dto;

import lombok.Getter;

import java.util.List;

// 리텐션 현황 (API-051) — 프론트가 streak을 읽는다. currentStreak/submittedToday도 함께 내려 호환.
// submitDays는 달력에 점 찍을 날짜(yyyy-MM-dd). 예전엔 브라우저에만 있어서 DB를 비워도 안 지워졌다.
// streak은 "연속" 학습일이라 하루 걸리면 0으로 떨어진다. 대시보드 상단이 보여줄
// 누적 학습일은 그 값으로 못 만들어서 totalDays를 따로 내린다
@Getter
public class RetentionStatusResponseDTO {

    private final int streak;
    private final int currentStreak;
    private final boolean submittedToday;
    private final List<String> submitDays;
    private final int totalDays;

    private RetentionStatusResponseDTO(int streak, boolean submittedToday, List<String> submitDays, int totalDays) {
        this.streak = streak;
        this.currentStreak = streak;
        this.submittedToday = submittedToday;
        this.submitDays = submitDays;
        this.totalDays = totalDays;
    }

    public static RetentionStatusResponseDTO of(int streak, boolean submittedToday, List<String> submitDays, int totalDays) {
        return new RetentionStatusResponseDTO(streak, submittedToday, submitDays, totalDays);
    }
}
