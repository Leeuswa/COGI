package idu.sba.backend.domain.pr.service;

import idu.sba.backend.domain.pr.entity.PullRequest;
import idu.sba.backend.domain.pr.repository.PullRequestRepository;
import idu.sba.backend.domain.repo.client.GithubApiClient;
import idu.sba.backend.domain.repo.client.GithubPrFileDto;
import idu.sba.backend.domain.repo.client.GithubPrSummaryDto;
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
    @Mock private GithubApiClient githubApiClient;

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
        PullRequest pr = PullRequest.open(REPO_ID, 42, "title", null, null);
        setField(pr, "id", PR_ID);
        return pr;
    }

    private GithubRepository repo() {
        GithubRepository repo = GithubRepository.link(USER_ID, "999", "repo-name", false, "owner/repo-name");
        setField(repo, "id", REPO_ID);
        return repo;
    }

    private User userWithToken(String token) {
        User user = User.createByGithub("gh-" + USER_ID, "user-gh", "user@test.com", token);
        setField(user, "id", USER_ID);
        return user;
    }

    private GithubPrSummaryDto prSummary(Integer number, String title, String authorLogin) {
        GithubPrSummaryDto dto = new GithubPrSummaryDto();
        setField(dto, "number", number);
        setField(dto, "title", title);
        if (authorLogin != null) {
            GithubPrSummaryDto.GithubPrUserDto user = new GithubPrSummaryDto.GithubPrUserDto();
            setField(user, "login", authorLogin);
            setField(dto, "user", user);
        }
        return dto;
    }

    private GithubPrFileDto prFile(String filename, String patch) {
        GithubPrFileDto dto = new GithubPrFileDto();
        setField(dto, "filename", filename);
        setField(dto, "patch", patch);
        return dto;
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

    @Test
    void authorId가_없어도_authorLogin이_있으면_그걸_작성자명으로_보여준다() {
        PullRequest pr = pr();
        setField(pr, "authorLogin", "outside-contributor"); //COGI 미가입자(webhook payload에만 있던 로그인명)
        when(pullRequestRepository.findById(PR_ID)).thenReturn(Optional.of(pr));
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, USER_ID)).thenReturn(true);
        when(reviewRepository.findByPrId(PR_ID)).thenReturn(Optional.empty());

        var result = service.getPrReview(USER_ID, PR_ID);

        assertThat(result.getPr().getAuthorName()).isEqualTo("outside-contributor");
    }

    @Test
    void authorId가_있어도_유저가_안_남아있으면_authorLogin으로_폴백한다() {
        PullRequest pr = pr();
        setField(pr, "authorId", 77L);
        setField(pr, "authorLogin", "author-gh");
        when(pullRequestRepository.findById(PR_ID)).thenReturn(Optional.of(pr));
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, USER_ID)).thenReturn(true);
        when(reviewRepository.findByPrId(PR_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(77L)).thenReturn(Optional.empty()); //탈퇴 등으로 사라진 케이스

        var result = service.getPrReview(USER_ID, PR_ID);

        assertThat(result.getPr().getAuthorName()).isEqualTo("author-gh");
    }

    // ---------- listOpenPrs ----------

    @Test
    void listOpenPrs_레포_팀원이_아니면_NOT_REPO_MEMBER() {
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.listOpenPrs(USER_ID, REPO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_REPO_MEMBER);
    }

    @Test
    void listOpenPrs_GitHub_미연동이면_GITHUB_NOT_LINKED() {
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, USER_ID)).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithToken(null)));

        assertThatThrownBy(() -> service.listOpenPrs(USER_ID, REPO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GITHUB_NOT_LINKED);
    }

    @Test
    void listOpenPrs_정상_케이스면_GithubApiClient_결과를_DTO로_변환한다() {
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, USER_ID)).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithToken("caller-token")));
        when(githubApiClient.listPullRequests("caller-token", "owner/repo-name"))
                .thenReturn(List.of(prSummary(42, "제목", "author-gh")));

        var result = service.listOpenPrs(USER_ID, REPO_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNumber()).isEqualTo(42);
        assertThat(result.get(0).getTitle()).isEqualTo("제목");
        assertThat(result.get(0).getAuthorLogin()).isEqualTo("author-gh");
    }

    // ---------- listPrFiles ----------

    @Test
    void listPrFiles_레포_팀원이_아니면_NOT_REPO_MEMBER() {
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.listPrFiles(USER_ID, REPO_ID, 42))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_REPO_MEMBER);
    }

    @Test
    void listPrFiles_patch가_없는_파일은_제외된다() {
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, USER_ID)).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithToken("caller-token")));
        when(githubApiClient.listPrFiles("caller-token", "owner/repo-name", 42)).thenReturn(List.of(
                prFile("Foo.java", "@@ -1 +1 @@\n-old\n+new"),
                prFile("image.png", null))); //바이너리 — patch 없음

        var result = service.listPrFiles(USER_ID, REPO_ID, 42);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPath()).isEqualTo("Foo.java");
        assertThat(result.get(0).getCode()).contains("+new");
    }

    // ---------- listReviewedPrs (API-032, 팀 대신 레포 기준 [설계 추론]) ----------

    @Test
    void listReviewedPrs_레포_팀원이_아니면_NOT_REPO_MEMBER() {
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.listReviewedPrs(USER_ID, REPO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_REPO_MEMBER);
    }

    @Test
    void listReviewedPrs_이슈개수와_최고심각도를_포함해_반환한다() {
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(repoMemberRepository.existsByRepoIdAndUserId(REPO_ID, USER_ID)).thenReturn(true);
        when(pullRequestRepository.findByRepoIdOrderByCreatedAtDesc(REPO_ID)).thenReturn(List.of(pr()));
        Review review = Review.createFromPr(USER_ID, PR_ID, "claude-haiku-4-5");
        setField(review, "id", 900L);
        when(reviewRepository.findByPrId(PR_ID)).thenReturn(Optional.of(review));
        when(reviewIssueRepository.findByReviewId(900L)).thenReturn(List.of(
                ReviewIssue.of(900L, IssueCategory.BUG, IssueSeverity.MINOR, "Foo.java", 1, "설명1"),
                ReviewIssue.of(900L, IssueCategory.BUG, IssueSeverity.CRITICAL, "Foo.java", 2, "설명2")));

        var result = service.listReviewedPrs(USER_ID, REPO_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIssueCount()).isEqualTo(2);
        assertThat(result.get(0).getTopSeverity()).isEqualTo("CRITICAL");
    }

}
