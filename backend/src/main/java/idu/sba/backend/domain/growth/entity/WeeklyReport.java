package idu.sba.backend.domain.growth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "weekly-reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId; //리포트 대상
    private LocalDate periodStart; // 월요일
    private LocalDate periodEnd; //일요일
    private int issueCount; // 그 주 이슈 발생 건
    private int resolvedCount; //그 주 해결 건
    private int prevIssueCount; //전주 발생(전주 대비 계산용)
    private String topCategory; //최다 카테고리

    @Column(columnDefinition = "TEXT")
    private String summary; //자동 생성 요약 문장

    @Column(updatable = false)
    private LocalDate createdAt;

    @PrePersist
    void onCreate(){
        this.createdAt = LocalDate.now();
    }

    private WeeklyReport(Long userId, LocalDate periodStart, LocalDate periodEnd,
                         int issueCount, int resolvedCount, int prevIssueCount, String topCategory, String summary) {
        this.userId = userId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.issueCount = issueCount;
        this.resolvedCount = resolvedCount;
        this.prevIssueCount = prevIssueCount;
        this.topCategory = topCategory;
        this.summary = summary;
    }

    public static WeeklyReport of(Long userId, LocalDate periodStart, LocalDate periodEnd,
                                  int issueCount, int resolvedCount, int prevIssueCount, String topCategory, String summary) {
        return new WeeklyReport(userId, periodStart, periodEnd, issueCount, resolvedCount, prevIssueCount, topCategory, summary);
    }

}
