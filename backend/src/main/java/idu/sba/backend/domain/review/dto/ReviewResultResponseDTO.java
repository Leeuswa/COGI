package idu.sba.backend.domain.review.dto;

import idu.sba.backend.domain.review.entity.Review;
import lombok.Getter;

import java.util.List;

@Getter
public class ReviewResultResponseDTO {

    private final Long reviewId;
    private final String modelName;
    private final String status;
    private final List<ReviewIssueResponseDTO> issues;

    private ReviewResultResponseDTO(Long reviewId, String modelName, String status, List<ReviewIssueResponseDTO> issues) {
        this.reviewId = reviewId;
        this.modelName = modelName;
        this.status = status;
        this.issues = issues;
    }

    public static ReviewResultResponseDTO of(Review review, List<ReviewIssueResponseDTO> issues) {
        return new ReviewResultResponseDTO(review.getId(), review.getModelName(), review.getStatus().name(), issues);
    }

}
