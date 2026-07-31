package idu.sba.backend.domain.pr.dto;

import idu.sba.backend.domain.review.dto.ReviewIssueResponseDTO;
import idu.sba.backend.domain.review.dto.ReviewQuestionResponseDTO;
import lombok.Getter;

import java.util.List;

//API-025: GET /api/prs/{prId}/review 응답 — PR 정보 + 이슈 목록(리뷰가 아직 없으면 빈 목록)
@Getter
public class PrReviewResponseDTO {

    private final PrDetailResponseDTO pr;
    private final List<ReviewIssueResponseDTO> issues;
    private final List<PrReviewHistoryItemDTO> reviewHistory; //최신순, 재검토가 없었으면 1건
    private final List<ReviewQuestionResponseDTO> questions; //최신 리뷰에 달린 후속 질문 기록(2026-07-29), 시간순

    private PrReviewResponseDTO(PrDetailResponseDTO pr, List<ReviewIssueResponseDTO> issues,
                                 List<PrReviewHistoryItemDTO> reviewHistory,
                                 List<ReviewQuestionResponseDTO> questions) {
        this.pr = pr;
        this.issues = issues;
        this.reviewHistory = reviewHistory;
        this.questions = questions;
    }

    public static PrReviewResponseDTO of(PrDetailResponseDTO pr, List<ReviewIssueResponseDTO> issues,
                                          List<PrReviewHistoryItemDTO> reviewHistory,
                                          List<ReviewQuestionResponseDTO> questions) {
        return new PrReviewResponseDTO(pr, issues, reviewHistory, questions);
    }

}
