package idu.sba.backend.domain.review.dto;

import idu.sba.backend.domain.review.entity.Review;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class ReviewHistoryDetailResponseDTO {

    private final Long reviewId;
    private final String targetType;
    private final String modelName;
    private final String status;
    private final String originalFilename; //nullable — UPLOAD만
    private final LocalDateTime createdAt;
    private final List<ReviewIssueResponseDTO> issues;

    private ReviewHistoryDetailResponseDTO(Long reviewId, String targetType, String modelName, String status,
                                            String originalFilename, LocalDateTime createdAt,
                                            List<ReviewIssueResponseDTO> issues) {
        this.reviewId = reviewId;
        this.targetType = targetType;
        this.modelName = modelName;
        this.status = status;
        this.originalFilename = originalFilename;
        this.createdAt = createdAt;
        this.issues = issues;
    }

    public static ReviewHistoryDetailResponseDTO of(Review review, List<ReviewIssueResponseDTO> issues) {
        return new ReviewHistoryDetailResponseDTO(review.getId(), review.getTargetType().name(),
                review.getModelName(), review.getStatus().name(), review.getOriginalFilename(),
                review.getCreatedAt(), issues);
    }

}
