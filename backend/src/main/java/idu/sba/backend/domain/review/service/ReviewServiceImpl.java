package idu.sba.backend.domain.review.service;

import idu.sba.backend.domain.payment.entity.Plan;
import idu.sba.backend.domain.payment.service.CreditUsageService;
import idu.sba.backend.domain.payment.service.SubscriptionService;
import idu.sba.backend.domain.review.client.AiReviewClient;
import idu.sba.backend.domain.review.client.AiReviewIssueDto;
import idu.sba.backend.domain.review.client.AiReviewRequest;
import idu.sba.backend.domain.review.client.AiReviewResult;
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
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final String INPUT_TYPE_PASTED = "pasted_code";
    private static final String REQUEST_TYPE_REVIEW = "REVIEW";

    private final ReviewRepository reviewRepository;
    private final ReviewIssueRepository reviewIssueRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final CreditUsageService creditUsageService;
    private final PromptBuilder promptBuilder;
    private final AiReviewClient aiReviewClient;

    @Override
    @Transactional
    public ReviewResultResponseDTO createFromPaste(Long userId, ReviewPasteRequestDTO request) {
        User user = requireUser(userId);
        Plan plan = subscriptionService.getCurrentPlanEntity(userId);
        String modelName = resolveModelName(request.getModelName(), plan);

        creditUsageService.checkAndConsume(userId, modelName); //모델 검증 이후에 소모 — 잘못된 요청으로 크레딧 낭비 방지

        Review review = Review.createPaste(userId, request.getCode(), request.getLanguage(), modelName);
        reviewRepository.save(review);

        List<ReviewIssue> issues = runReview(review, user, plan, modelName, request.getCode(), request.getLanguage());
        return toResponse(review, issues);
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

        List<ReviewIssue> issues = runReview(review, user, plan, modelName, request.getCode(), request.getLanguage());
        return toResponse(review, issues);
    }

    // 프롬프트 조합 → AI 호출 → 이슈/사용량 저장까지, paste/upload 두 경로가 공유하는 핵심 로직.
    // AI 호출이 실패하면(BusinessException) 메서드 전체가 @Transactional이라 credit 소모를 포함해 전부 롤백된다
    // — 동기 요청/응답 흐름이라 실패 시 review row를 남기지 않고 그냥 에러로 응답한다(FAILED 상태는 비동기 PR 웹훅 경로용, 이번 범위 밖).
    private List<ReviewIssue> runReview(Review review, User user, Plan plan, String modelName, String code, String language) {
        Level level = user.getLevel() != null ? user.getLevel() : Level.BEGINNER; //온보딩 전(level null)이면 초급 취급
        String systemPrompt = promptBuilder.build(level, plan.getName());

        AiReviewResult result = aiReviewClient.review(
                new AiReviewRequest(modelName, systemPrompt, code, language, INPUT_TYPE_PASTED));

        List<ReviewIssue> issues = result.getIssues().stream()
                .map(dto -> toIssueEntity(review.getId(), dto))
                .toList();
        reviewIssueRepository.saveAll(issues);

        aiUsageLogRepository.save(AiUsageLog.of(review.getUserId(), modelName,
                result.getInputTokens(), result.getOutputTokens(), result.getCost(), REQUEST_TYPE_REVIEW));

        review.markCompleted();
        return issues;
    }

    private ReviewIssue toIssueEntity(Long reviewId, AiReviewIssueDto dto) {
        try {
            return ReviewIssue.of(reviewId,
                    IssueCategory.valueOf(dto.getCategory()),
                    IssueSeverity.valueOf(dto.getSeverity()),
                    dto.getFilePath(), dto.getLineNumber(), dto.getDescription());
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

    private ReviewResultResponseDTO toResponse(Review review, List<ReviewIssue> issues) {
        return ReviewResultResponseDTO.of(review, issues.stream().map(ReviewIssueResponseDTO::of).toList());
    }

}
