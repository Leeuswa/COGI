package idu.sba.backend.domain.pr.dto;

import idu.sba.backend.domain.pr.entity.PullRequest;
import idu.sba.backend.domain.review.entity.IssueSeverity;
import idu.sba.backend.domain.review.entity.ReviewIssue;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

// PR 리뷰 대시보드(API-032, 팀 대신 레포 기준으로 축소) 목록 항목 [설계 추론]
@Getter
public class PrListItemResponseDTO {

    private final Long id;
    private final Integer githubPrNumber;
    private final String title;
    private final String authorName; //nullable — 작성자가 COGI 미가입자면 null
    private final String status;
    private final int issueCount;
    private final String topSeverity; //nullable — 이슈 없으면 null
    private final LocalDateTime createdAt;

    private PrListItemResponseDTO(Long id, Integer githubPrNumber, String title, String authorName,
                                   String status, int issueCount, String topSeverity, LocalDateTime createdAt) {
        this.id = id;
        this.githubPrNumber = githubPrNumber;
        this.title = title;
        this.authorName = authorName;
        this.status = status;
        this.issueCount = issueCount;
        this.topSeverity = topSeverity;
        this.createdAt = createdAt;
    }

    public static PrListItemResponseDTO of(PullRequest pr, String authorName, List<ReviewIssue> issues) {
        String topSeverity = issues.stream()
                .map(ReviewIssue::getSeverity)
                .min(Comparator.comparingInt(PrListItemResponseDTO::severityRank))
                .map(Enum::name)
                .orElse(null);
        return new PrListItemResponseDTO(pr.getId(), pr.getGithubPrNumber(), pr.getTitle(), authorName,
                pr.getStatus().name(), issues.size(), topSeverity, pr.getCreatedAt());
    }

    private static int severityRank(IssueSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 0;
            case MAJOR -> 1;
            case MINOR -> 2;
        };
    }

}
