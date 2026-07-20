package idu.sba.backend.domain.review.dto;

import idu.sba.backend.domain.review.entity.IssueSeverity;
import idu.sba.backend.domain.review.entity.Review;
import idu.sba.backend.domain.review.entity.ReviewIssue;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Getter
public class ReviewHistoryItemResponseDTO {

    private final Long reviewId;
    private final String targetType;
    private final String modelName;
    private final String status;
    private final String originalFilename; //nullable — UPLOAD만
    private final LocalDateTime createdAt;
    private final int issueCount;
    private final String topSeverity; //nullable — 이슈 없으면 null

    private ReviewHistoryItemResponseDTO(Long reviewId, String targetType, String modelName, String status,
                                          String originalFilename, LocalDateTime createdAt,
                                          int issueCount, String topSeverity) {
        this.reviewId = reviewId;
        this.targetType = targetType;
        this.modelName = modelName;
        this.status = status;
        this.originalFilename = originalFilename;
        this.createdAt = createdAt;
        this.issueCount = issueCount;
        this.topSeverity = topSeverity;
    }

    public static ReviewHistoryItemResponseDTO of(Review review, List<ReviewIssue> issues) {
        String topSeverity = issues.stream()
                .map(ReviewIssue::getSeverity)
                .min(Comparator.comparingInt(ReviewHistoryItemResponseDTO::severityRank))
                .map(Enum::name)
                .orElse(null);
        return new ReviewHistoryItemResponseDTO(review.getId(), review.getTargetType().name(),
                review.getModelName(), review.getStatus().name(), review.getOriginalFilename(),
                review.getCreatedAt(), issues.size(), topSeverity);
    }

    private static int severityRank(IssueSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 0;
            case MAJOR -> 1;
            case MINOR -> 2;
        };
    }

}
