package idu.sba.backend.domain.review.service;

import idu.sba.backend.domain.pr.entity.PullRequest;
import idu.sba.backend.domain.pr.repository.PullRequestRepository;
import idu.sba.backend.domain.repo.entity.RepoMember;
import idu.sba.backend.domain.repo.entity.RepoRole;
import idu.sba.backend.domain.repo.repository.RepoMemberRepository;
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
    @Mock private PullRequestRepository pullRequestRepository;
    @Mock private RepoMemberRepository repoMemberRepository;

    @InjectMocks
    private ReviewIssueServiceImpl service;

    private static final Long USER_ID = 1L;
    private static final Long REVIEW_ID = 10L;
    private static final Long ISSUE_ID = 100L;
    private static final Long REPO_ID = 5L;
    private static final Long PR_ID = 50L;
    private static final Long OWNER_ID = 2L;

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

    private ReviewIssue issue(IssueSeverity severity) {
        ReviewIssue issue = ReviewIssue.of(REVIEW_ID, IssueCategory.BUG, severity, "Foo.java", 10, "설명");
        setField(issue, "id", ISSUE_ID);
        return issue;
    }

    private Review reviewOwnedBy(Long userId) {
        Review review = Review.createPaste(userId, "code", "java", "claude-haiku-4-5");
        setField(review, "id", REVIEW_ID);
        return review;
    }

    private Review prReviewOwnedBy(Long userId) {
        Review review = Review.createFromPr(userId, PR_ID, "claude-haiku-4-5");
        setField(review, "id", REVIEW_ID);
        return review;
    }

    private PullRequest pullRequest() {
        PullRequest pr = PullRequest.open(REPO_ID, 42, "title", null, null);
        setField(pr, "id", PR_ID);
        return pr;
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
    void RESOLVED로_확정하면_고칠_예정이라_통계에서_빠지지_않는다() {
        ReviewIssue issue = issue();
        when(reviewIssueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(reviewOwnedBy(USER_ID)));

        service.finalizeIssue(USER_ID, ISSUE_ID, "RESOLVED");

        assertThat(issue.getStatus()).isEqualTo(IssueStatus.RESOLVED);
        assertThat(issue.isAcknowledged()).isFalse(); //실제 약점 패턴이 맞으므로 통계 제외 대상이 아님
    }

    @Test
    void IGNORED로_확정하면_의도한_코드라_통계에서_제외된다() {
        ReviewIssue issue = issue();
        when(reviewIssueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(reviewOwnedBy(USER_ID)));

        service.finalizeIssue(USER_ID, ISSUE_ID, "IGNORED");

        assertThat(issue.getStatus()).isEqualTo(IssueStatus.IGNORED);
        assertThat(issue.isAcknowledged()).isTrue();
    }

    // ---------- 이슈 승인 흐름(RDB-003 [설계 추론]) ----------

    @Test
    void PR_리뷰의_CRITICAL_이슈를_RESOLVED로_확정하면_바로_안_풀리고_승인_대기로_간다() {
        ReviewIssue issue = issue(IssueSeverity.CRITICAL);
        when(reviewIssueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(prReviewOwnedBy(USER_ID)));

        service.finalizeIssue(USER_ID, ISSUE_ID, "RESOLVED");

        assertThat(issue.getStatus()).isEqualTo(IssueStatus.PENDING);
        assertThat(issue.getRequestType()).isEqualTo("RESOLVE_REQUEST");
    }

    @Test
    void PR_리뷰라도_MAJOR_이슈는_승인_없이_바로_RESOLVED된다() {
        ReviewIssue issue = issue(IssueSeverity.MAJOR);
        when(reviewIssueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(prReviewOwnedBy(USER_ID)));

        service.finalizeIssue(USER_ID, ISSUE_ID, "RESOLVED");

        assertThat(issue.getStatus()).isEqualTo(IssueStatus.RESOLVED);
    }

    @Test
    void 개인_리뷰는_CRITICAL이어도_승인_없이_바로_RESOLVED된다() {
        ReviewIssue issue = issue(IssueSeverity.CRITICAL);
        when(reviewIssueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(reviewOwnedBy(USER_ID))); //PASTE 리뷰

        service.finalizeIssue(USER_ID, ISSUE_ID, "RESOLVED");

        assertThat(issue.getStatus()).isEqualTo(IssueStatus.RESOLVED);
    }

    @Test
    void decideResolveRequest_PENDING이_아니면_INVALID_INPUT() {
        ReviewIssue issue = issue(); // 기본 status=OPEN
        when(reviewIssueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));

        assertThatThrownBy(() -> service.decideResolveRequest(OWNER_ID, ISSUE_ID, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void decideResolveRequest_레포_팀원이_아니면_NOT_REPO_MEMBER() {
        ReviewIssue issue = issue(IssueSeverity.CRITICAL);
        issue.requestResolve();
        when(reviewIssueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(prReviewOwnedBy(USER_ID)));
        when(pullRequestRepository.findById(PR_ID)).thenReturn(Optional.of(pullRequest()));
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decideResolveRequest(OWNER_ID, ISSUE_ID, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_REPO_MEMBER);
    }

    @Test
    void decideResolveRequest_팀장이_아니면_NOT_REPO_OWNER() {
        ReviewIssue issue = issue(IssueSeverity.CRITICAL);
        issue.requestResolve();
        when(reviewIssueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(prReviewOwnedBy(USER_ID)));
        when(pullRequestRepository.findById(PR_ID)).thenReturn(Optional.of(pullRequest()));
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, USER_ID))
                .thenReturn(Optional.of(RepoMember.of(REPO_ID, USER_ID, RepoRole.MEMBER)));

        assertThatThrownBy(() -> service.decideResolveRequest(USER_ID, ISSUE_ID, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_REPO_OWNER);
    }

    @Test
    void decideResolveRequest_팀장이_승인하면_RESOLVED되고_approvedBy가_남는다() {
        ReviewIssue issue = issue(IssueSeverity.CRITICAL);
        issue.requestResolve();
        when(reviewIssueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(prReviewOwnedBy(USER_ID)));
        when(pullRequestRepository.findById(PR_ID)).thenReturn(Optional.of(pullRequest()));
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, OWNER_ID))
                .thenReturn(Optional.of(RepoMember.of(REPO_ID, OWNER_ID, RepoRole.OWNER)));

        service.decideResolveRequest(OWNER_ID, ISSUE_ID, true);

        assertThat(issue.getStatus()).isEqualTo(IssueStatus.RESOLVED);
        assertThat(issue.getApprovedBy()).isEqualTo(OWNER_ID);
    }

    @Test
    void decideResolveRequest_팀장이_반려하면_OPEN으로_돌아간다() {
        ReviewIssue issue = issue(IssueSeverity.CRITICAL);
        issue.requestResolve();
        when(reviewIssueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(prReviewOwnedBy(USER_ID)));
        when(pullRequestRepository.findById(PR_ID)).thenReturn(Optional.of(pullRequest()));
        when(repoMemberRepository.findByRepoIdAndUserId(REPO_ID, OWNER_ID))
                .thenReturn(Optional.of(RepoMember.of(REPO_ID, OWNER_ID, RepoRole.OWNER)));

        service.decideResolveRequest(OWNER_ID, ISSUE_ID, false);

        assertThat(issue.getStatus()).isEqualTo(IssueStatus.OPEN);
        assertThat(issue.getRequestType()).isNull();
    }

}
