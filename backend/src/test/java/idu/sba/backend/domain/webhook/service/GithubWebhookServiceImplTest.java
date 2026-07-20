package idu.sba.backend.domain.webhook.service;

import idu.sba.backend.domain.pr.entity.PullRequest;
import idu.sba.backend.domain.pr.entity.PullRequestStatus;
import idu.sba.backend.domain.pr.repository.PullRequestRepository;
import idu.sba.backend.domain.repo.entity.GithubRepository;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.review.service.ReviewService;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.domain.webhook.dto.GithubPullRequestEventPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GithubWebhookServiceImplTest {

    @Mock private GithubRepositoryRepository githubRepositoryRepository;
    @Mock private PullRequestRepository pullRequestRepository;
    @Mock private UserRepository userRepository;
    @Mock private ReviewService reviewService;

    @InjectMocks
    private GithubWebhookServiceImpl service;

    private static final Long REPO_ID = 1L;

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private GithubRepository repo(String fullName) {
        GithubRepository repo = GithubRepository.link(10L, "999", "repo-name", false, fullName);
        setField(repo, "id", REPO_ID);
        return repo;
    }

    private GithubPullRequestEventPayload payload(String action, Integer prNumber, String title,
                                                    String authorLogin, Long githubRepoId, String fullName) {
        return new GithubPullRequestEventPayload(
                action, prNumber,
                new GithubPullRequestEventPayload.GithubPrPayload(prNumber, title,
                        authorLogin == null ? null : new GithubPullRequestEventPayload.GithubUserPayload(authorLogin)),
                new GithubPullRequestEventPayload.GithubRepoPayload(githubRepoId, fullName));
    }

    @Test
    void 연동되지_않은_레포에서_온_웹훅은_조용히_무시한다() {
        when(githubRepositoryRepository.findByGithubRepoId("999")).thenReturn(Optional.empty());

        service.handlePullRequestEvent(payload("opened", 1, "title", null, 999L, "owner/repo"));

        verify(pullRequestRepository, never()).save(any());
        verify(reviewService, never()).createFromPr(any());
    }

    @Test
    void 신규_PR이면_생성하고_리뷰를_요청한다() {
        GithubRepository repo = repo("owner/repo-name");
        when(githubRepositoryRepository.findByGithubRepoId("999")).thenReturn(Optional.of(repo));
        when(pullRequestRepository.findByRepoIdAndGithubPrNumber(REPO_ID, 42)).thenReturn(Optional.empty());
        when(userRepository.findByGithubUsername("author-gh")).thenReturn(Optional.empty());
        when(pullRequestRepository.save(any())).thenAnswer(inv -> {
            var pr = inv.getArgument(0, PullRequest.class);
            setField(pr, "id", 500L);
            return pr;
        });

        service.handlePullRequestEvent(payload("opened", 42, "새 PR", "author-gh", 999L, "owner/repo-name"));

        verify(pullRequestRepository).save(any());
        verify(reviewService).createFromPr(500L);
        verify(githubRepositoryRepository, never()).save(any()); //fullName 이미 있으니 self-heal 불필요
    }

    @Test
    void 기존_PR이면_reopenForReview로_재사용한다() {
        GithubRepository repo = repo("owner/repo-name");
        PullRequest existing = PullRequest.open(REPO_ID, 42, "옛 제목", null);
        setField(existing, "id", 500L);
        setField(existing, "status", PullRequestStatus.FAILED); //이전에 실패했던 PR이 synchronize로 다시 옴

        when(githubRepositoryRepository.findByGithubRepoId("999")).thenReturn(Optional.of(repo));
        when(pullRequestRepository.findByRepoIdAndGithubPrNumber(REPO_ID, 42)).thenReturn(Optional.of(existing));
        when(pullRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handlePullRequestEvent(payload("synchronize", 42, "새 제목", null, 999L, "owner/repo-name"));

        assertThat(existing.getTitle()).isEqualTo("새 제목");
        assertThat(existing.getStatus()).isEqualTo(PullRequestStatus.OPEN); //재리뷰 대상으로 되돌아감
        verify(reviewService).createFromPr(500L);
    }

    @Test
    void fullName이_없으면_payload로_self_heal한다() {
        GithubRepository repo = repo(null); //마이그레이션 이전 레포
        when(githubRepositoryRepository.findByGithubRepoId("999")).thenReturn(Optional.of(repo));
        when(pullRequestRepository.findByRepoIdAndGithubPrNumber(any(), any())).thenReturn(Optional.empty());
        when(pullRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handlePullRequestEvent(payload("opened", 1, "title", null, 999L, "owner/repo-name"));

        assertThat(repo.getFullName()).isEqualTo("owner/repo-name");
        verify(githubRepositoryRepository).save(repo);
    }

    @Test
    void 작성자_githubUsername이_매칭되면_authorId가_채워진다() {
        GithubRepository repo = repo("owner/repo-name");
        User author = User.createByGithub("gh-99", "author-gh", "author@test.com", "token");
        setField(author, "id", 77L);

        when(githubRepositoryRepository.findByGithubRepoId("999")).thenReturn(Optional.of(repo));
        when(pullRequestRepository.findByRepoIdAndGithubPrNumber(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findByGithubUsername("author-gh")).thenReturn(Optional.of(author));
        when(pullRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handlePullRequestEvent(payload("opened", 1, "title", "author-gh", 999L, "owner/repo-name"));

        var captor = org.mockito.ArgumentCaptor.forClass(PullRequest.class);
        verify(pullRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthorId()).isEqualTo(77L);
    }

}
