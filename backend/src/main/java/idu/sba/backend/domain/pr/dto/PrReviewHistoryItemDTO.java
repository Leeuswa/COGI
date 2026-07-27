package idu.sba.backend.domain.pr.dto;

import lombok.Getter;

import java.time.LocalDateTime;

//PR 재검토(웹훅 synchronize)로 쌓인 리뷰 이력 한 건 — "고쳐져서 재검토에 안 나오면 판정 이력이 안 남는다"는
//한계를 완전히 없애진 못하지만, 최소한 리뷰가 몇 번 돌았고 CRITICAL이 몇 건에서 몇 건으로 줄었는지는 보여준다.
@Getter
public class PrReviewHistoryItemDTO {

    private final Long reviewId;
    private final LocalDateTime createdAt;
    private final String modelName;
    private final String status;
    private final int issueCount;
    private final int criticalCount;

    private PrReviewHistoryItemDTO(Long reviewId, LocalDateTime createdAt, String modelName, String status,
                                    int issueCount, int criticalCount) {
        this.reviewId = reviewId;
        this.createdAt = createdAt;
        this.modelName = modelName;
        this.status = status;
        this.issueCount = issueCount;
        this.criticalCount = criticalCount;
    }

    public static PrReviewHistoryItemDTO of(Long reviewId, LocalDateTime createdAt, String modelName, String status,
                                             int issueCount, int criticalCount) {
        return new PrReviewHistoryItemDTO(reviewId, createdAt, modelName, status, issueCount, criticalCount);
    }

}
