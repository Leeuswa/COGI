package idu.sba.backend.domain.pr.service;

import idu.sba.backend.domain.pr.entity.PullRequest;
import idu.sba.backend.domain.pr.repository.PullRequestRepository;
import idu.sba.backend.domain.repo.entity.GithubRepository;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.repo.repository.RepoMemberRepository;
import idu.sba.backend.domain.review.entity.IssueCategory;
import idu.sba.backend.domain.review.entity.IssueSeverity;
import idu.sba.backend.domain.review.entity.Review;
import idu.sba.backend.domain.review.entity.ReviewIssue;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrServiceImplTest {

    @Mock private PullRequestRepository pullRequestRepository;
    @Mock private GithubRepositoryRepository githubRepositoryRepository;
    @Mock private RepoMemberRepository repoMemberRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewIssueRepository reviewIssueRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private PrServiceImpl service;

    private static final Long PR_ID = 100L;
    private static final Long REPO_ID = 1L;
    private static final Long USER_ID = 10L;

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private PullRequest pr() {
        PullRequest pr = PullRequest.open(REPO_ID, 42, "title", null);
        setField(pr, "id", PR_ID);
        return pr;
    }

    private GithubRepository repo() {
        GithubRepository repo = GithubRepository.link(USER_ID, "999", "repo-name", false, "owner/repo-name");
        setField(repo, "id", REPO_ID);
        return repo;
    }

    @Test
    void PR이_없으면_PR_NOT_FOUND() {
        when(pullRequestRepository.findById(PR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPrReview(USER_ID, PR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PR_NOT_FOUND);
    }

    @Test
    void 호출자가_레포_팀원이_아니면_NOT_REPO_MEMBER() {
        when(pullRequestRepository.findById(PR_ID)).thenReturn(Optional.of(pr()));
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getPrReview(USER_ID, PR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_REPO_MEMBER);
    }

    @Test
    void 아직_리뷰가_없으면_빈_이슈목록을_반환한다() {
        when(pullRequestRepository.findById(PR_ID)).thenReturn(Optional.of(pr()));
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, USER_ID)).thenReturn(true);
        when(reviewRepository.findByPrId(PR_ID)).thenReturn(Optional.empty());

        var result = service.getPrReview(USER_ID, PR_ID);

        assertThat(result.getIssues()).isEmpty();
        assertThat(result.getPr().getStatus()).isEqualTo("OPEN");
    }

    @Test
    void 리뷰가_완료됐으면_이슈목록과_작성자명을_반환한다() {
        PullRequest pr = pr();
        setField(pr, "authorId", 77L);
        Review review = Review.createFromPr(USER_ID, PR_ID, "claude-haiku-4-5");
        setField(review, "id", 900L);
        ReviewIssue issue = ReviewIssue.of(900L, IssueCategory.BUG, IssueSeverity.CRITICAL, "Foo.java", 10, "설명");
        User author = User.createByGithub("gh-77", "author-gh", "author@test.com", "token");
        setField(author, "nickname", "작성자닉네임");

        when(pullRequestRepository.findById(PR_ID)).thenReturn(Optional.of(pr));
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, USER_ID)).thenReturn(true);
        when(reviewRepository.findByPrId(PR_ID)).thenReturn(Optional.of(review));
        when(reviewIssueRepository.findByReviewId(900L)).thenReturn(List.of(issue));
        when(userRepository.findById(77L)).thenReturn(Optional.of(author));

        var result = service.getPrReview(USER_ID, PR_ID);

        assertThat(result.getIssues()).hasSize(1);
        assertThat(result.getPr().getAuthorName()).isEqualTo("작성자닉네임");
    }

    @Test
    void authorId가_null이면_유저_조회_없이_authorName도_null() {
        when(pullRequestRepository.findById(PR_ID)).thenReturn(Optional.of(pr())); //authorId 없음
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, USER_ID)).thenReturn(true);
        when(reviewRepository.findByPrId(PR_ID)).thenReturn(Optional.empty());

        var result = service.getPrReview(USER_ID, PR_ID);

        assertThat(result.getPr().getAuthorName()).isNull();
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).findById(any());
    }

}
