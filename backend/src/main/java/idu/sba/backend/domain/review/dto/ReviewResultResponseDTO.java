package idu.sba.backend.domain.review.dto;

import idu.sba.backend.domain.review.entity.Review;
import lombok.Getter;

import java.util.List;

@Getter
public class ReviewResultResponseDTO {

    private final Long reviewId;
    private final String modelName;
    private final String status;
    private final String summary;      // AI 응답 요약 — analyzable=false일 때 프론트가 이슈 목록 대신 보여줄 문장
    private final boolean analyzable;  // false면 입력이 코드가 아니거나 분석 불가(크레딧은 환불됨)
    private final List<ReviewIssueResponseDTO> issues;

    private ReviewResultResponseDTO(Long reviewId, String modelName, String status,
                                     String summary, boolean analyzable, List<ReviewIssueResponseDTO> issues) {
        this.reviewId = reviewId;
        this.modelName = modelName;
        this.status = status;
        this.summary = summary;
        this.analyzable = analyzable;
        this.issues = issues;
    }

    public static ReviewResultResponseDTO of(Review review, List<ReviewIssueResponseDTO> issues,
                                              String summary, boolean analyzable) {
        return new ReviewResultResponseDTO(review.getId(), review.getModelName(), review.getStatus().name(),
                summary, analyzable, issues);
    }

}
