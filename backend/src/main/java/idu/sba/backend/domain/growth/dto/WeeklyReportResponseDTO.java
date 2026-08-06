package idu.sba.backend.domain.growth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class WeeklyReportResponseDTO {
    private final Long id;
    private final String periodStart;   // "yyyy-MM-dd"
    private final String periodEnd;
    private final int issueCount;
    private final int resolvedCount;
    private final Integer prevIssueCount; // 전주 없으면 null (프론트가 null이면 비교 스킵)
    private final String topCategory;
    private final List<Category> categories;
    private final int quizSubmits;   // 학습활동은 프론트(GameContext) 소관 → MVP 0
    private final int correctRate;
    private final int streakEnd;
    private final String summary;
    private final List<String> actions;

    @Getter
    @AllArgsConstructor
    public static class Category {
        private final String name;
        private final int count;
    }
}