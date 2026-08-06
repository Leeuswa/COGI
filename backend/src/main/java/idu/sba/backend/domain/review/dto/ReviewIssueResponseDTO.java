package idu.sba.backend.domain.review.dto;

import idu.sba.backend.domain.review.entity.ReviewIssue;
import lombok.Getter;

@Getter
public class ReviewIssueResponseDTO {

    private final Long id;
    private final String category;
    private final String severity;
    private final String filePath;
    private final Integer lineNumber;
    private final String description;
    private final String status;
    private final boolean acknowledged;

    private ReviewIssueResponseDTO(Long id, String category, String severity, String filePath,
                                    Integer lineNumber, String description, String status, boolean acknowledged) {
        this.id = id;
        this.category = category;
        this.severity = severity;
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.description = description;
        this.status = status;
        this.acknowledged = acknowledged;
    }

    public static ReviewIssueResponseDTO of(ReviewIssue issue) {
        return new ReviewIssueResponseDTO(issue.getId(), issue.getCategory().name(), issue.getSeverity().name(),
                issue.getFilePath(), issue.getLineNumber(), issue.getDescription(),
                issue.getStatus().name(), issue.isAcknowledged());
    }

}
