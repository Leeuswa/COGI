package idu.sba.backend.domain.review.service;

import idu.sba.backend.domain.payment.entity.Plan;
import idu.sba.backend.domain.payment.service.CreditUsageService;
import idu.sba.backend.domain.payment.service.SubscriptionService;
import idu.sba.backend.domain.pr.entity.PullRequest;
import idu.sba.backend.domain.pr.entity.PullRequestStatus;
import idu.sba.backend.domain.pr.repository.PullRequestRepository;
import idu.sba.backend.domain.repo.client.GithubApiClient;
import idu.sba.backend.domain.repo.client.GithubPrFileDto;
import idu.sba.backend.domain.repo.entity.GithubRepository;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.review.dto.ReviewPasteRequestDTO;
import idu.sba.backend.domain.review.entity.ReviewStatus;
import idu.sba.backend.domain.review.repository.AiUsageLogRepository;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.ai.AiReviewClient;
import idu.sba.backend.global.ai.AiReviewIssue;
import idu.sba.backend.global.ai.AiReviewResult;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewIssueRepository reviewIssueRepository;
    @Mock private AiUsageLogRepository aiUsageLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubscriptionService subscriptionService;
    @Mock private CreditUsageService creditUsageService;
    @Mock private PromptBuilder promptBuilder;
    @Mock private AiReviewClient aiReviewClient;
    @Mock private PullRequestRepository pullRequestRepository;
    @Mock private GithubRepositoryRepository githubRepositoryRepository;
    @Mock private GithubApiClient githubApiClient;

    @InjectMocks
    private ReviewServiceImpl service;

    private static final Long USER_ID = 1L;
    private static final Long OWNER_ID = 2L;
    private static final Long REPO_ID = 5L;
    private static final Long PR_ID = 50L;

    private User freeUser() {
        User user = User.createByGithub("gh-1", "user-gh", "user@test.com", "token");
        setField(user, "id", USER_ID);
        return user;
    }

    private Plan freePlan() {
        Plan plan = new Plan();
        setField(plan, "name", "FREE");
        setField(plan, "dailyCreditLimit", 20);
        setField(plan, "allowedModels", "claude-haiku-4-5,gpt-5.6-luna,gemini-3.5-flash");
        setField(plan, "price", 0);
        return plan;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private ReviewPasteRequestDTO pasteRequest(String code, String language, String modelName) {
        ReviewPasteRequestDTO request = new ReviewPasteRequestDTO();
        setField(request, "code", code);
        setField(request, "language", language);
        setField(request, "modelName", modelName);
        return request;
    }

    private User ownerUser(String accessToken) {
        User user = User.createByGithub("gh-owner", "owner-gh", "owner@test.com", accessToken);
        setField(user, "id", OWNER_ID);
        return user;
    }

    private GithubRepository repo() {
        GithubRepository repo = GithubRepository.link(OWNER_ID, "999", "repo-name", false, "owner/repo-name");
        setField(repo, "id", REPO_ID);
        return repo;
    }

    private PullRequest pr() {
        PullRequest pr = PullRequest.open(REPO_ID, 42, "title", null);
        setField(pr, "id", PR_ID);
        return pr;
    }

    private GithubPrFileDto prFile(String filename, String patch) {
        GithubPrFileDto dto = new GithubPrFileDto();
        setField(dto, "filename", filename);
        setField(dto, "patch", patch);
        setField(dto, "status", "modified");
        return dto;
    }

    @Test
    void 플랜에서_허용하지_않는_모델이면_크레딧_소모_없이_예외() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(freeUser()));
        when(subscriptionService.getCurrentPlanEntity(USER_ID)).thenReturn(freePlan());

        assertThatThrownBy(() -> service.createFromPaste(USER_ID,
                pasteRequest("code", "java", "claude-opus-4-8")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MODEL_NOT_ALLOWED_FOR_PLAN);

        verify(creditUsageService, never()).checkAndConsume(any(), any());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void 크레딧_한도_초과면_리뷰가_저장되지_않는다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(freeUser()));
        when(subscriptionService.getCurrentPlanEntity(USER_ID)).thenReturn(freePlan());
        doThrow(new BusinessException(ErrorCode.CREDIT_LIMIT_EXCEEDED))
                .when(creditUsageService).checkAndConsume(eq(USER_ID), any());

        assertThatThrownBy(() -> service.createFromPaste(USER_ID,
                pasteRequest("code", "java", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CREDIT_LIMIT_EXCEEDED);

        verify(reviewRepository, never()).save(any());
        verify(aiReviewClient, never()).review(any(), any(), any(), any(), any());
    }

    @Test
    void 정상_리뷰_요청시_이슈와_사용량이_저장되고_완료_상태로_반환된다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(freeUser()));
        when(subscriptionService.getCurrentPlanEntity(USER_ID)).thenReturn(freePlan());
        when(promptBuilder.build(any(), eq("FREE"))).thenReturn("system-prompt");
        when(aiReviewClient.review(any(), any(), any(), any(), any())).thenReturn(new AiReviewResult(
                "요약", List.of(new AiReviewIssue("BUG", "CRITICAL", "Foo.java", 10, "null 체크 누락")),
                100, 50, 0.01, true));
        when(reviewRepository.save(any())).thenAnswer(inv -> {
            var review = inv.getArgument(0, idu.sba.backend.domain.review.entity.Review.class);
            setField(review, "id", 999L);
            return review;
        });

        var result = service.createFromPaste(USER_ID, pasteRequest("bad code", "java", null));

        assertThat(result.getReviewId()).isEqualTo(999L);
        assertThat(result.getModelName()).isEqualTo("claude-haiku-4-5"); //플랜의 첫 허용 모델(기본값)
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getIssues()).hasSize(1);
        assertThat(result.getIssues().get(0).getCategory()).isEqualTo("BUG");

        verify(creditUsageService).checkAndConsume(USER_ID, "claude-haiku-4-5");
        verify(reviewIssueRepository).saveAll(anyList());
        verify(aiUsageLogRepository).save(any());
    }

    @Test
    void AI가_analyzable_false로_응답하면_이슈없이_완료되고_크레딧이_환불된다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(freeUser()));
        when(subscriptionService.getCurrentPlanEntity(USER_ID)).thenReturn(freePlan());
        when(promptBuilder.build(any(), eq("FREE"))).thenReturn("system-prompt");
        when(aiReviewClient.review(any(), any(), any(), any(), any())).thenReturn(new AiReviewResult(
                "코드가 아니라 분석할 수 없습니다", List.of(), 20, 10, 0.001, false));
        when(reviewRepository.save(any())).thenAnswer(inv -> {
            var review = inv.getArgument(0, idu.sba.backend.domain.review.entity.Review.class);
            setField(review, "id", 1000L);
            return review;
        });

        var result = service.createFromPaste(USER_ID, pasteRequest("안녕하세요", "java", null));

        assertThat(result.isAnalyzable()).isFalse();
        assertThat(result.getSummary()).isEqualTo("코드가 아니라 분석할 수 없습니다");
        assertThat(result.getIssues()).isEmpty();
        assertThat(result.getStatus()).isEqualTo("COMPLETED");

        verify(creditUsageService).checkAndConsume(USER_ID, "claude-haiku-4-5");
        verify(creditUsageService).refund(USER_ID, "claude-haiku-4-5");
        verify(reviewIssueRepository, never()).saveAll(anyList());
        verify(aiUsageLogRepository).save(any()); // 벤더 호출 비용은 analyzable 여부와 무관하게 기록
    }

    @Test
    void AI_응답의_category가_스키마를_벗어나면_AI_MODEL_CALL_FAILED() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(freeUser()));
        when(subscriptionService.getCurrentPlanEntity(USER_ID)).thenReturn(freePlan());
        when(promptBuilder.build(any(), eq("FREE"))).thenReturn("system-prompt");
        when(aiReviewClient.review(any(), any(), any(), any(), any())).thenReturn(new AiReviewResult(
                null, List.of(new AiReviewIssue("NOT_A_REAL_CATEGORY", "CRITICAL", "Foo.java", 10, "설명")),
                100, 50, 0.01, true));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.createFromPaste(USER_ID, pasteRequest("bad code", "java", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_MODEL_CALL_FAILED);
    }

    @Test
    void 모델_옵션_조회는_플랜의_allowedModels를_그대로_분리해서_반환한다() {
        when(subscriptionService.getCurrentPlanEntity(USER_ID)).thenReturn(freePlan());

        var options = service.getModelOptions(USER_ID);

        assertThat(options).containsExactly("claude-haiku-4-5", "gpt-5.6-luna", "gemini-3.5-flash");
    }

    // ---------- createFromPr (API-024 웹훅 리뷰) ----------

    @Test
    void createFromPr_정상_케이스면_레포_팀장_기준으로_크레딧을_소모하고_리뷰완료로_기록된다() {
        PullRequest pr = pr();
        when(pullRequestRepository.findById(PR_ID)).thenReturn(Optional.of(pr));
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(ownerUser("owner-token")));
        when(subscriptionService.getCurrentPlanEntity(OWNER_ID)).thenReturn(freePlan());
        when(promptBuilder.build(any(), eq("FREE"))).thenReturn("system-prompt");
        when(githubApiClient.listPrFiles("owner-token", "owner/repo-name", 42))
                .thenReturn(List.of(prFile("Foo.java", "@@ -1 +1 @@\n-old\n+new")));
        when(aiReviewClient.review(any(), any(), any(), any(), any())).thenReturn(new AiReviewResult(
                "요약", List.of(new AiReviewIssue("BUG", "CRITICAL", "Foo.java", 10, "설명")),
                100, 50, 0.01, true));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createFromPr(PR_ID);

        verify(creditUsageService).checkAndConsume(OWNER_ID, "claude-haiku-4-5"); //PR 작성자가 아니라 팀장 기준
        assertThat(pr.getStatus()).isEqualTo(PullRequestStatus.REVIEWED);
        verify(reviewIssueRepository).saveAll(anyList());
    }

    @Test
    void createFromPr_크레딧_초과시_예외를_던지지_않고_PR을_실패로_기록한다() {
        PullRequest pr = pr();
        when(pullRequestRepository.findById(PR_ID)).thenReturn(Optional.of(pr));
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(ownerUser("owner-token")));
        when(subscriptionService.getCurrentPlanEntity(OWNER_ID)).thenReturn(freePlan());
        doThrow(new BusinessException(ErrorCode.CREDIT_LIMIT_EXCEEDED))
                .when(creditUsageService).checkAndConsume(eq(OWNER_ID), any());

        service.createFromPr(PR_ID); //예외가 밖으로 안 던져지는 것 자체가 검증 대상

        assertThat(pr.getStatus()).isEqualTo(PullRequestStatus.FAILED);
        verify(githubApiClient, never()).listPrFiles(any(), any(), anyInt());
        verify(reviewRepository, never()).save(any());
        verify(creditUsageService, never()).refund(any(), any()); //애초에 소모가 안 됐으니 환불도 없어야 함
    }

    @Test
    void createFromPr_GitHub_API_실패시_이미_소모한_크레딧을_환불하고_PR을_실패로_기록한다() {
        PullRequest pr = pr();
        when(pullRequestRepository.findById(PR_ID)).thenReturn(Optional.of(pr));
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(ownerUser("owner-token")));
        when(subscriptionService.getCurrentPlanEntity(OWNER_ID)).thenReturn(freePlan());
        when(githubApiClient.listPrFiles(any(), any(), anyInt()))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_API_ERROR));

        service.createFromPr(PR_ID);

        assertThat(pr.getStatus()).isEqualTo(PullRequestStatus.FAILED);
        verify(reviewRepository, never()).save(any());
        verify(creditUsageService).checkAndConsume(OWNER_ID, "claude-haiku-4-5"); //이미 소모됐었고
        verify(creditUsageService).refund(OWNER_ID, "claude-haiku-4-5"); //실패했으니 되돌려야 함
    }

    @Test
    void createFromPr_AI_호출_실패시_Review와_PR_둘_다_실패로_기록한다() {
        PullRequest pr = pr();
        when(pullRequestRepository.findById(PR_ID)).thenReturn(Optional.of(pr));
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(ownerUser("owner-token")));
        when(subscriptionService.getCurrentPlanEntity(OWNER_ID)).thenReturn(freePlan());
        when(promptBuilder.build(any(), eq("FREE"))).thenReturn("system-prompt");
        when(githubApiClient.listPrFiles(any(), any(), anyInt()))
                .thenReturn(List.of(prFile("Foo.java", "diff")));
        when(aiReviewClient.review(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createFromPr(PR_ID);

        assertThat(pr.getStatus()).isEqualTo(PullRequestStatus.FAILED);
        var captor = org.mockito.ArgumentCaptor.forClass(idu.sba.backend.domain.review.entity.Review.class);
        verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReviewStatus.FAILED);
        verify(creditUsageService).refund(OWNER_ID, "claude-haiku-4-5"); //이미 소모된 크레딧은 되돌려야 함
    }

    @Test
    void createFromPr_레포_팀장이_GitHub_미연동이면_크레딧_소모_없이_PR을_실패로_기록한다() {
        PullRequest pr = pr();
        when(pullRequestRepository.findById(PR_ID)).thenReturn(Optional.of(pr));
        when(githubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(ownerUser(null))); //토큰 없음

        service.createFromPr(PR_ID);

        assertThat(pr.getStatus()).isEqualTo(PullRequestStatus.FAILED);
        verify(subscriptionService, never()).getCurrentPlanEntity(any());
        verify(creditUsageService, never()).refund(any(), any()); //modelName조차 안 정해졌으니 환불 대상 아님
    }

}
