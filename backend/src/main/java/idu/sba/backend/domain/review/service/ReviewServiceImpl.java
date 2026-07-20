package idu.sba.backend.domain.review.service;

import idu.sba.backend.domain.payment.entity.Plan;
import idu.sba.backend.domain.payment.service.CreditUsageService;
import idu.sba.backend.domain.payment.service.SubscriptionService;
import idu.sba.backend.domain.pr.entity.PullRequest;
import idu.sba.backend.domain.pr.repository.PullRequestRepository;
import idu.sba.backend.domain.repo.client.GithubApiClient;
import idu.sba.backend.domain.repo.entity.GithubRepository;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.review.dto.ReviewHistoryDetailResponseDTO;
import idu.sba.backend.domain.review.dto.ReviewHistoryItemResponseDTO;
import idu.sba.backend.domain.review.dto.ReviewIssueResponseDTO;
import idu.sba.backend.domain.review.dto.ReviewPasteRequestDTO;
import idu.sba.backend.domain.review.dto.ReviewResultResponseDTO;
import idu.sba.backend.domain.review.dto.ReviewUploadRequestDTO;
import idu.sba.backend.domain.review.entity.AiUsageLog;
import idu.sba.backend.domain.review.entity.IssueCategory;
import idu.sba.backend.domain.review.entity.IssueSeverity;
import idu.sba.backend.domain.review.entity.Review;
import idu.sba.backend.domain.review.entity.ReviewIssue;
import idu.sba.backend.domain.review.repository.AiUsageLogRepository;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewRepository;
import idu.sba.backend.domain.user.entity.Level;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.ai.AiInputType;
import idu.sba.backend.global.ai.AiModel;
import idu.sba.backend.global.ai.AiReviewClient;
import idu.sba.backend.global.ai.AiReviewIssue;
import idu.sba.backend.global.ai.AiReviewResult;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final String REQUEST_TYPE_REVIEW = "REVIEW";

    private final ReviewRepository reviewRepository;
    private final ReviewIssueRepository reviewIssueRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final CreditUsageService creditUsageService;
    private final PromptBuilder promptBuilder;
    private final AiReviewClient aiReviewClient;
    private final PullRequestRepository pullRequestRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubApiClient githubApiClient;

    @Override
    @Transactional
    public ReviewResultResponseDTO createFromPaste(Long userId, ReviewPasteRequestDTO request) {
        User user = requireUser(userId);
        Plan plan = subscriptionService.getCurrentPlanEntity(userId);
        String modelName = resolveModelName(request.getModelName(), plan);

        creditUsageService.checkAndConsume(userId, modelName); //모델 검증 이후에 소모 — 잘못된 요청으로 크레딧 낭비 방지

        Review review = Review.createPaste(userId, request.getCode(), request.getLanguage(), modelName);
        reviewRepository.save(review);

        ReviewOutcome outcome = runReview(review, user, plan, modelName, request.getCode(), request.getLanguage(), AiInputType.PASTED_CODE);
        return toResponse(review, outcome);
    }

    @Override
    @Transactional
    public ReviewResultResponseDTO createFromUpload(Long userId, ReviewUploadRequestDTO request) {
        User user = requireUser(userId);
        Plan plan = subscriptionService.getCurrentPlanEntity(userId);
        String modelName = resolveModelName(request.getModelName(), plan);

        creditUsageService.checkAndConsume(userId, modelName);

        Review review = Review.createUpload(userId, request.getCode(), request.getLanguage(),
                request.getOriginalFilename(), modelName);
        reviewRepository.save(review);

        ReviewOutcome outcome = runReview(review, user, plan, modelName, request.getCode(), request.getLanguage(), AiInputType.PASTED_CODE);
        return toResponse(review, outcome);
    }

    // API-024: 웹훅으로 감지된 PR 리뷰 — GithubWebhookServiceImpl이 로컬 DB 작업을 이미 커밋한 뒤 호출한다.
    // HTTP 호출자가 없어 응답을 보여줄 곳이 없으므로, 실패해도 절대 예외를 밖으로 던지지 않고
    // PullRequest.status(+Review.status)에 흡수한다 — paste/upload와 달리 실패를 "에러 응답"이 아니라
    // "영구 기록"으로 다뤄야 하기 때문(제약: 크레딧/모델은 항상 레포 팀장 기준 — PR 작성자가 미가입자여도 동작).
    @Override
    @Async("prReviewExecutor")
    @Transactional
    public void createFromPr(Long pullRequestId) {
        PullRequest pr = pullRequestRepository.findById(pullRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PR_NOT_FOUND));
        Review review = null;
        Long ownerId = null;
        String modelName = null;
        try {
            GithubRepository repo = githubRepositoryRepository.findById(pr.getRepoId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.REPO_NOT_FOUND));

            ownerId = repo.getUserId(); //제약: 항상 레포 팀장 기준(PR 작성자가 COGI 미가입자여도 동작해야 함)
            User owner = requireUser(ownerId);
            if (owner.getGithubAccessToken() == null) {
                throw new BusinessException(ErrorCode.GITHUB_NOT_LINKED);
            }

            Plan plan = subscriptionService.getCurrentPlanEntity(ownerId);
            modelName = resolveModelName(pr.getSelectedModel(), plan); //null → 플랜 기본 모델(API-028 이번 범위 밖)
            creditUsageService.checkAndConsume(ownerId, modelName);

            String diffText = fetchDiffText(owner.getGithubAccessToken(), repo.getFullName(), pr.getGithubPrNumber());

            review = Review.createFromPr(ownerId, pr.getId(), modelName);
            reviewRepository.save(review);

            runReview(review, owner, plan, modelName, diffText, null, AiInputType.PR_DIFF);
            pr.markReviewed();
        } catch (BusinessException e) {
            log.error("PR 리뷰 처리 실패 - prId={}, errorCode={}", pullRequestId, e.getErrorCode());
            if (review != null) {
                review.markFailed();
            }
            // CREDIT_LIMIT_EXCEEDED 자체는 체크 단계에서 막혀 애초에 소모가 안 됨 — 그 뒤(GitHub API 호출, AI 호출 등)에서
            // 실패하면 이미 소모된 크레딧을 되돌려야 한다. 안 그러면 리뷰가 실제로 되지도 않았는데 팀장 크레딧만 깎인다.
            if (ownerId != null && modelName != null && e.getErrorCode() != ErrorCode.CREDIT_LIMIT_EXCEEDED) {
                creditUsageService.refund(ownerId, modelName);
            }
            pr.markFailed();
            //의도적으로 다시 던지지 않음 — 호출자가 없어 예외를 보여줄 곳이 없고, 실패는 PullRequest.status로 영구 기록됨
        }
    }

    //바이너리/과대용량 diff는 patch가 없어 GitHub가 생략함 — 그런 파일은 스킵
    private String fetchDiffText(String accessToken, String repoFullName, Integer prNumber) {
        return githubApiClient.listPrFiles(accessToken, repoFullName, prNumber).stream()
                .filter(f -> f.getPatch() != null)
                .map(f -> "파일: " + f.getFilename() + "\n" + f.getPatch())
                .collect(Collectors.joining("\n\n"));
    }

    // runReview의 결과 — 이슈 목록뿐 아니라 프론트가 analyzable=false 분기 렌더링에 쓸 summary/analyzable도 함께 반환
    private record ReviewOutcome(List<ReviewIssue> issues, String summary, boolean analyzable) {}

    // 프롬프트 조합 → AI 호출 → 이슈/사용량 저장까지, paste/upload 두 경로가 공유하는 핵심 로직.
    // AI 호출이 실패하면(BusinessException) 메서드 전체가 @Transactional이라 credit 소모를 포함해 전부 롤백된다
    // — 동기 요청/응답 흐름이라 실패 시 review row를 남기지 않고 그냥 에러로 응답한다(FAILED 상태는 비동기 PR 웹훅 경로용, 이번 범위 밖).
    private ReviewOutcome runReview(Review review, User user, Plan plan, String modelName, String code, String language, AiInputType inputType) {
        Level level = user.getLevel() != null ? user.getLevel() : Level.BEGINNER; //온보딩 전(level null)이면 초급 취급
        String systemPrompt = promptBuilder.build(level, plan.getName());
        AiModel model = resolveAiModel(modelName);

        AiReviewResult result = aiReviewClient.review(model, systemPrompt, code, language, inputType);

        // 벤더 호출 자체는 실제로 일어나 비용이 발생했으므로, analyzable 여부와 무관하게 사용량은 그대로 기록한다.
        aiUsageLogRepository.save(AiUsageLog.of(review.getUserId(), modelName,
                result.inputTokens(), result.outputTokens(), result.cost(), REQUEST_TYPE_REVIEW));

        if (!result.analyzable()) {
            // 코드가 아니거나 분석 불가한 입력 — 사용자 크레딧은 소모 전으로 되돌리고 이슈 없이 종료
            creditUsageService.refund(review.getUserId(), modelName);
            review.markCompleted();
            return new ReviewOutcome(List.of(), result.summary(), false);
        }

        List<ReviewIssue> issues = result.issues().stream()
                .map(issue -> toIssueEntity(review.getId(), issue))
                .toList();
        reviewIssueRepository.saveAll(issues);

        review.markCompleted();
        return new ReviewOutcome(issues, result.summary(), true);
    }

    // resolveModelName이 이미 plan.allowedModels 기준으로 검증했으므로 정상적으로는 항상 매핑되지만,
    // AiModel enum과 plans.allowed_models 목록이 어긋나는 경우(설정 드리프트)에 대한 방어
    private AiModel resolveAiModel(String modelName) {
        try {
            return AiModel.fromId(modelName);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED);
        }
    }

    private ReviewIssue toIssueEntity(Long reviewId, AiReviewIssue issue) {
        try {
            return ReviewIssue.of(reviewId,
                    IssueCategory.valueOf(issue.category()),
                    IssueSeverity.valueOf(issue.severity()),
                    issue.filePath(), issue.lineNumber(), issue.description());
        } catch (IllegalArgumentException e) { //AI가 _common_rules.txt의 고정 스키마를 벗어난 값을 준 경우
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED);
        }
    }

    // modelName 미지정 시 플랜의 첫 번째 허용 모델을 기본값으로 사용
    private String resolveModelName(String requestedModel, Plan plan) {
        List<String> allowed = splitAllowedModels(plan);

        if (requestedModel == null || requestedModel.isBlank()) {
            return allowed.get(0);
        }
        if (!allowed.contains(requestedModel)) {
            throw new BusinessException(ErrorCode.MODEL_NOT_ALLOWED_FOR_PLAN);
        }
        return requestedModel;
    }

    @Override
    public List<String> getModelOptions(Long userId) {
        Plan plan = subscriptionService.getCurrentPlanEntity(userId);
        return splitAllowedModels(plan);
    }

    private List<String> splitAllowedModels(Plan plan) {
        return Arrays.stream(plan.getAllowedModels().split(","))
                .map(String::trim)
                .toList();
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private ReviewResultResponseDTO toResponse(Review review, ReviewOutcome outcome) {
        return ReviewResultResponseDTO.of(review, outcome.issues().stream().map(ReviewIssueResponseDTO::of).toList(),
                outcome.summary(), outcome.analyzable());
    }

    @Override
    public List<ReviewHistoryItemResponseDTO> getHistory(Long userId) {
        List<Review> reviews = reviewRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Long> reviewIds = reviews.stream().map(Review::getId).toList();
        Map<Long, List<ReviewIssue>> issuesByReviewId = reviewIssueRepository.findByReviewIdIn(reviewIds).stream()
                .collect(Collectors.groupingBy(ReviewIssue::getReviewId));

        return reviews.stream()
                .map(review -> ReviewHistoryItemResponseDTO.of(review,
                        issuesByReviewId.getOrDefault(review.getId(), List.of())))
                .toList();
    }

    @Override
    public ReviewHistoryDetailResponseDTO getHistoryDetail(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        if (!userId.equals(review.getUserId())) {
            throw new BusinessException(ErrorCode.REVIEW_ACCESS_DENIED);
        }
        List<ReviewIssueResponseDTO> issues = reviewIssueRepository.findByReviewId(reviewId).stream()
                .map(ReviewIssueResponseDTO::of)
                .toList();
        return ReviewHistoryDetailResponseDTO.of(review, issues);
    }

}
