package idu.sba.backend.domain.webhook.service;

import idu.sba.backend.domain.pr.entity.PullRequest;
import idu.sba.backend.domain.pr.repository.PullRequestRepository;
import idu.sba.backend.domain.repo.entity.GithubRepository;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.review.service.ReviewService;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.domain.webhook.dto.GithubPullRequestEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

//⚠️ 이 클래스의 메서드에 절대 @Transactional을 걸지 않는다 — reviewService.createFromPr(...)가
//@Async로 별도 스레드(별도 커넥션)에서 실행되는데, 이 메서드가 트랜잭션으로 묶여 있으면 그 스레드가
//시작될 때 아직 커밋 전이라 방금 저장한 PullRequest row를 못 찾는 레이스 컨디션이 생긴다.
//대신 각 저장을 개별 호출로 즉시 커밋시킨 뒤에만 createFromPr을 호출한다(트랜잭션이 없으면 JPA dirty
//checking이 안 먹으므로 mutate 후 save()를 명시적으로 불러야 하는 것도 이 방식의 자연스러운 결과).
@Slf4j
@Service
@RequiredArgsConstructor
public class GithubWebhookServiceImpl implements GithubWebhookService {

    private final GithubRepositoryRepository githubRepositoryRepository;
    private final PullRequestRepository pullRequestRepository;
    private final UserRepository userRepository;
    private final ReviewService reviewService;

    @Override
    public void handlePullRequestEvent(GithubPullRequestEventPayload payload) {
        GithubRepository repo = githubRepositoryRepository
                .findByGithubRepoId(String.valueOf(payload.repository().id()))
                .orElse(null);
        if (repo == null) {
            log.info("연동되지 않은 레포에서 온 웹훅 - githubRepoId={}", payload.repository().id());
            return; //우리가 모르는 레포 — 조용히 무시
        }
        if (repo.getFullName() == null) {
            //마이그레이션 이전 레포는 fullName이 없었음 — 웹훅 payload로 self-heal(추가 API 호출 불필요)
            repo.assignFullName(payload.repository().fullName());
            githubRepositoryRepository.save(repo);
        }

        Long authorId = resolveAuthorId(payload);
        String authorLogin = payload.pullRequest().user() == null ? null : payload.pullRequest().user().login();

        PullRequest pr = pullRequestRepository
                .findByRepoIdAndGithubPrNumber(repo.getId(), payload.number())
                .map(existing -> {
                    existing.reopenForReview(payload.pullRequest().title(), authorId, authorLogin);
                    return existing;
                })
                .orElseGet(() -> PullRequest.open(repo.getId(), payload.number(), payload.pullRequest().title(), authorId, authorLogin));
        pullRequestRepository.save(pr);

        reviewService.createFromPr(pr.getId()); //@Async — 이 시점엔 위 저장이 이미 커밋된 상태
    }

    private Long resolveAuthorId(GithubPullRequestEventPayload payload) {
        if (payload.pullRequest().user() == null || payload.pullRequest().user().login() == null) {
            return null;
        }
        return userRepository.findByGithubUsername(payload.pullRequest().user().login())
                .map(User::getId)
                .orElse(null);
    }

}
