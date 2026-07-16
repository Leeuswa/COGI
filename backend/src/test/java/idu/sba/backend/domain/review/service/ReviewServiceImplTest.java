package idu.sba.backend.domain.review.service;

import idu.sba.backend.domain.payment.entity.Plan;
import idu.sba.backend.domain.payment.service.CreditUsageService;
import idu.sba.backend.domain.payment.service.SubscriptionService;
import idu.sba.backend.domain.review.client.AiReviewClient;
import idu.sba.backend.domain.review.client.AiReviewIssueDto;
import idu.sba.backend.domain.review.client.AiReviewResult;
import idu.sba.backend.domain.review.dto.ReviewPasteRequestDTO;
import idu.sba.backend.domain.review.repository.AiUsageLogRepository;
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

    @InjectMocks
    private ReviewServiceImpl service;

    private static final Long USER_ID = 1L;

    private User freeUser() {
        User user = User.createByGithub("gh-1", "user-gh", "user@test.com", "token");
        setField(user, "id", USER_ID);
        return user;
    }

    private Plan freePlan() {
        Plan plan = new Plan();
        setField(plan, "name", "FREE");
        setField(plan, "dailyCreditLimit", 20);
        setField(plan, "allowedModels", "claude-haiku-4-5,gpt-5.6-luna,gemini-3.5-flash-lite");
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
        verify(aiReviewClient, never()).review(any());
    }

    @Test
    void 정상_리뷰_요청시_이슈와_사용량이_저장되고_완료_상태로_반환된다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(freeUser()));
        when(subscriptionService.getCurrentPlanEntity(USER_ID)).thenReturn(freePlan());
        when(promptBuilder.build(any(), eq("FREE"))).thenReturn("system-prompt");
        when(aiReviewClient.review(any())).thenReturn(new AiReviewResult(
                List.of(new AiReviewIssueDto("BUG", "CRITICAL", "Foo.java", 10, "null 체크 누락")),
                100, 50, 0.01));
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
    void AI_응답의_category가_스키마를_벗어나면_AI_MODEL_CALL_FAILED() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(freeUser()));
        when(subscriptionService.getCurrentPlanEntity(USER_ID)).thenReturn(freePlan());
        when(promptBuilder.build(any(), eq("FREE"))).thenReturn("system-prompt");
        when(aiReviewClient.review(any())).thenReturn(new AiReviewResult(
                List.of(new AiReviewIssueDto("NOT_A_REAL_CATEGORY", "CRITICAL", "Foo.java", 10, "설명")),
                100, 50, 0.01));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.createFromPaste(USER_ID, pasteRequest("bad code", "java", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_MODEL_CALL_FAILED);
    }

}
