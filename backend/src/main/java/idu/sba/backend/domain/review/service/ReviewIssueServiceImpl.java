package idu.sba.backend.domain.review.service;

import idu.sba.backend.domain.review.entity.IssueStatus;
import idu.sba.backend.domain.review.entity.Review;
import idu.sba.backend.domain.review.entity.ReviewIssue;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewIssueServiceImpl implements ReviewIssueService {

    private final ReviewIssueRepository reviewIssueRepository;
    private final ReviewRepository reviewRepository;

    @Override
    @Transactional
    public void finalizeIssue(Long currentUserId, Long issueId, String verdict) {
        ReviewIssue issue = reviewIssueRepository.findById(issueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ISSUE_NOT_FOUND));
        Review review = reviewRepository.findById(issue.getReviewId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ISSUE_NOT_FOUND)); // 이슈만 있고 리뷰가 없는 건 데이터 정합성 문제라 같은 코드로 취급

        if (!currentUserId.equals(review.getUserId())) {
            throw new BusinessException(ErrorCode.ISSUE_ACCESS_DENIED);
        }

        issue.finalizeAs(parseVerdict(verdict));
    }

    // 사용자가 실제로 고를 수 있는 판정은 RESOLVED/IGNORED뿐 — OPEN/PENDING은 판정이 아니라 시스템 상태값이라 여기로 못 들어옴
    private IssueStatus parseVerdict(String verdict) {
        IssueStatus status;
        try {
            status = IssueStatus.valueOf(verdict);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (status != IssueStatus.RESOLVED && status != IssueStatus.IGNORED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return status;
    }

}
