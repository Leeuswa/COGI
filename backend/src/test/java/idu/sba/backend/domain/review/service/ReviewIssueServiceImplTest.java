package idu.sba.backend.domain.review.service;

import idu.sba.backend.domain.review.entity.IssueCategory;
import idu.sba.backend.domain.review.entity.IssueSeverity;
import idu.sba.backend.domain.review.entity.IssueStatus;
import idu.sba.backend.domain.review.entity.Review;
import idu.sba.backend.domain.review.entity.ReviewIssue;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewIssueServiceImplTest {

    @Mock private ReviewIssueRepository reviewIssueRepository;
    @Mock private ReviewRepository reviewRepository;

    @InjectMocks
    private ReviewIssueServiceImpl service;

    private static final Long USER_ID = 1L;
    private static final Long REVIEW_ID = 10L;
    private static final Long ISSUE_ID = 100L;

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private ReviewIssue issue() {
        ReviewIssue issue = ReviewIssue.of(REVIEW_ID, IssueCategory.BUG, IssueSeverity.CRITICAL, "Foo.java", 10, "설명");
        setField(issue, "id", ISSUE_ID);
        return issue;
    }

    private Review reviewOwnedBy(Long userId) {
        Review review = Review.createPaste(userId, "code", "java", "claude-haiku-4-5");
        setField(review, "id", REVIEW_ID);
        return review;
    }

    @Test
    void 존재하지_않는_이슈면_ISSUE_NOT_FOUND() {
        when(reviewIssueRepository.findById(ISSUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.finalizeIssue(USER_ID, ISSUE_ID, "RESOLVED"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ISSUE_NOT_FOUND);
    }

    @Test
    void 본인의_리뷰가_아니면_ISSUE_ACCESS_DENIED() {
        when(reviewIssueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue()));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(reviewOwnedBy(999L)));

        assertThatThrownBy(() -> service.finalizeIssue(USER_ID, ISSUE_ID, "RESOLVED"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ISSUE_ACCESS_DENIED);
    }

    @Test
    void 유효하지_않은_verdict면_INVALID_INPUT() {
        when(reviewIssueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue()));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(reviewOwnedBy(USER_ID)));

        assertThatThrownBy(() -> service.finalizeIssue(USER_ID, ISSUE_ID, "SOMETHING_ELSE"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void OPEN이나_PENDING은_판정으로_못_씀() {
        when(reviewIssueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue()));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(reviewOwnedBy(USER_ID)));

        assertThatThrownBy(() -> service.finalizeIssue(USER_ID, ISSUE_ID, "OPEN"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 정상_케이스면_RESOLVED로_확정되고_acknowledged가_true가_된다() {
        ReviewIssue issue = issue();
        when(reviewIssueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(reviewOwnedBy(USER_ID)));

        service.finalizeIssue(USER_ID, ISSUE_ID, "RESOLVED");

        assertThat(issue.getStatus()).isEqualTo(IssueStatus.RESOLVED);
        assertThat(issue.isAcknowledged()).isTrue();
    }

    @Test
    void IGNORED로도_확정_가능하다() {
        ReviewIssue issue = issue();
        when(reviewIssueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(reviewOwnedBy(USER_ID)));

        service.finalizeIssue(USER_ID, ISSUE_ID, "IGNORED");

        assertThat(issue.getStatus()).isEqualTo(IssueStatus.IGNORED);
        assertThat(issue.isAcknowledged()).isTrue();
    }

}
