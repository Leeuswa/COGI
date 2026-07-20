package idu.sba.backend.domain.pr.dto;

import idu.sba.backend.domain.review.dto.ReviewIssueResponseDTO;
import lombok.Getter;

import java.util.List;

//API-025: GET /api/prs/{prId}/review 응답 — PR 정보 + 이슈 목록(리뷰가 아직 없으면 빈 목록)
@Getter
public class PrReviewResponseDTO {

    private final PrDetailResponseDTO pr;
    private final List<ReviewIssueResponseDTO> issues;

    private PrReviewResponseDTO(PrDetailResponseDTO pr, List<ReviewIssueResponseDTO> issues) {
        this.pr = pr;
        this.issues = issues;
    }

    public static PrReviewResponseDTO of(PrDetailResponseDTO pr, List<ReviewIssueResponseDTO> issues) {
        return new PrReviewResponseDTO(pr, issues);
    }

}
