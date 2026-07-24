package idu.sba.backend.domain.learning.service;

import idu.sba.backend.domain.learning.dto.CardHistoryResponseDTO;
import idu.sba.backend.domain.learning.dto.LearningCardCreateRequestDTO;
import idu.sba.backend.domain.learning.dto.LearningCardResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizSubmissionResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizSubmitResultDTO;
import idu.sba.backend.domain.learning.entity.LearningCard;
import idu.sba.backend.domain.learning.entity.LearningCardQuiz;
import idu.sba.backend.domain.learning.entity.QuizSubmission;
import idu.sba.backend.domain.learning.repository.LearningCardQuizRepository;
import idu.sba.backend.domain.learning.repository.LearningCardRepository;
import idu.sba.backend.domain.learning.repository.QuizSubmissionRepository;
import idu.sba.backend.domain.learning.repository.WeaknessStatRepository;
import idu.sba.backend.domain.retention.service.RetentionService;
import idu.sba.backend.domain.payment.entity.Plan;
import idu.sba.backend.domain.payment.service.CreditUsageService;
import idu.sba.backend.domain.payment.service.SubscriptionService;
import idu.sba.backend.domain.review.repository.AiUsageLogRepository;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewRepository;
import idu.sba.backend.global.ai.AiGenerationResult;
import idu.sba.backend.global.ai.AiModel;
import idu.sba.backend.global.ai.AiReviewClient;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningCardServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewIssueRepository reviewIssueRepository;
    @Mock private WeaknessStatRepository weaknessStatRepository;
    @Mock private LearningCardRepository learningCardRepository;
    @Mock private LearningCardQuizRepository learningCardQuizRepository;
    @Mock private QuizSubmissionRepository quizSubmissionRepository;
    @Mock private SubscriptionService subscriptionService;
    @Mock private CreditUsageService creditUsageService;
    @Mock private AiReviewClient aiReviewClient;
    @Mock private AiUsageLogRepository aiUsageLogRepository;
    @Mock private RetentionService retentionService;
    @Mock private Plan plan;

    private LearningServiceImpl service;

    private static final Long USER_ID = 1L;
    private static final Long CARD_ID = 100L;
    private static final Long QUIZ_ID = 500L;

    @BeforeEach
    void setUp() {
        // ObjectMapper는 실제 파싱을 검증해야 하므로 목이 아닌 실물을 주입
        service = new LearningServiceImpl(reviewRepository, reviewIssueRepository, weaknessStatRepository,
                learningCardRepository, learningCardQuizRepository, quizSubmissionRepository,
                subscriptionService, creditUsageService, aiReviewClient, aiUsageLogRepository, new ObjectMapper(),
                retentionService);
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

    private LearningCardCreateRequestDTO request(String category, String level, String language) {
        LearningCardCreateRequestDTO dto = new LearningCardCreateRequestDTO();
        setField(dto, "category", category);
        setField(dto, "level", level);
        setField(dto, "language", language);
        return dto;
    }

    private LearningCard cardOwnedBy(Long userId) {
        LearningCard card = LearningCard.create(userId, "null 안전성", "BEGINNER", "Java",
                "claude-haiku-4-5", "개념", "bad", "good", "k1\nk2\nk3");
        setField(card, "id", CARD_ID);
        return card;
    }

    @Test
    void 카드생성_AI응답을_파싱해_카드로_저장한다() {
        when(subscriptionService.getCurrentPlanEntity(USER_ID)).thenReturn(plan);
        when(plan.getAllowedModels()).thenReturn("claude-haiku-4-5");
        String aiJson = "{\"conceptContent\":\"개념 설명\",\"badExample\":\"before\",\"goodExample\":\"after\",\"keyPoints\":[\"핵심1\",\"핵심2\",\"핵심3\"]}";
        when(aiReviewClient.generate(any(AiModel.class), anyString(), anyString()))
                .thenReturn(new AiGenerationResult(aiJson, 100, 200, 0.01));
        when(learningCardRepository.save(any(LearningCard.class))).thenAnswer(inv -> inv.getArgument(0));

        LearningCardResponseDTO result = service.createCard(USER_ID, request("null 안전성", "BEGINNER", "Java"));

        assertThat(result.getConceptContent()).isEqualTo("개념 설명");
        assertThat(result.getBadExample()).isEqualTo("before");
        assertThat(result.getGoodExample()).isEqualTo("after");
        assertThat(result.getKeyPoints()).containsExactly("핵심1", "핵심2", "핵심3");
        assertThat(result.getGrade()).isEqualTo("RED"); // 새 카드는 빨강
        assertThat(result.getCorrectCount()).isZero();
        assertThat(result.isBookmarked()).isFalse();
        assertThat(result.getCategory()).isEqualTo("null 안전성");
        assertThat(result.getLanguage()).isEqualTo("Java");

        verify(creditUsageService).checkAndConsume(USER_ID, "claude-haiku-4-5"); // 크레딧 소모
        verify(aiUsageLogRepository).save(any()); // 사용량 기록
    }

    @Test
    void 카드생성_AI가_형식을_어기면_실패() {
        when(subscriptionService.getCurrentPlanEntity(USER_ID)).thenReturn(plan);
        when(plan.getAllowedModels()).thenReturn("claude-haiku-4-5");
        when(aiReviewClient.generate(any(AiModel.class), anyString(), anyString()))
                .thenReturn(new AiGenerationResult("이건 JSON이 아니야", 10, 20, 0.001));

        assertThatThrownBy(() -> service.createCard(USER_ID, request("null 안전성", "BEGINNER", "Java")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_MODEL_CALL_FAILED);
    }

    @Test
    void 없는_카드_조회면_LEARNING_CARD_NOT_FOUND() {
        when(learningCardRepository.findById(CARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCard(USER_ID, CARD_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEARNING_CARD_NOT_FOUND);
    }

    @Test
    void 남의_카드_조회면_LEARNING_CARD_ACCESS_DENIED() {
        when(learningCardRepository.findById(CARD_ID)).thenReturn(Optional.of(cardOwnedBy(999L)));

        assertThatThrownBy(() -> service.getCard(USER_ID, CARD_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEARNING_CARD_ACCESS_DENIED);
    }

    @Test
    void 북마크_토글은_본인_카드의_상태를_바꾼다() {
        LearningCard card = cardOwnedBy(USER_ID);
        when(learningCardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));

        service.toggleBookmark(USER_ID, CARD_ID, true);

        assertThat(card.isBookmarked()).isTrue();
    }

    @Test
    void 상세_조회는_핵심포인트를_배열로_돌려준다() {
        when(learningCardRepository.findById(CARD_ID)).thenReturn(Optional.of(cardOwnedBy(USER_ID)));

        LearningCardResponseDTO result = service.getCard(USER_ID, CARD_ID);

        assertThat(result.getKeyPoints()).containsExactly("k1", "k2", "k3");
        assertThat(result.isCompleted()).isFalse();
    }

    // ---------- 퀴즈 생성/제출/히스토리 (API-045/046/047) ----------

    private LearningCardQuiz quiz(Long cardId, String answer) {
        LearningCardQuiz q = LearningCardQuiz.create(cardId, "MULTIPLE_CHOICE", "문제", "A\nB\nC\nD", answer, "해설");
        setField(q, "id", QUIZ_ID);
        return q;
    }

    @Test
    void 퀴즈생성_카드를_만든_모델로_생성하고_저장한다() {
        // cardOwnedBy가 모델 claude-haiku-4-5로 생성된 카드 → 퀴즈도 그 모델로
        when(learningCardRepository.findById(CARD_ID)).thenReturn(Optional.of(cardOwnedBy(USER_ID)));
        String aiJson = "{\"question\":\"?. 의 이름은?\",\"options\":[\"옵셔널 체이닝\",\"널 병합\",\"전개\",\"OR\"],\"answer\":\"옵셔널 체이닝\",\"explain\":\"?. 는 옵셔널 체이닝이다\"}";
        when(aiReviewClient.generate(any(AiModel.class), anyString(), anyString()))
                .thenReturn(new AiGenerationResult(aiJson, 50, 80, 0.005));
        when(learningCardQuizRepository.save(any(LearningCardQuiz.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizResponseDTO result = service.createQuiz(USER_ID, CARD_ID, "MULTIPLE_CHOICE");

        assertThat(result.getQuestionType()).isEqualTo("MULTIPLE_CHOICE");
        assertThat(result.getQuestion()).isEqualTo("?. 의 이름은?");
        assertThat(result.getOptions()).containsExactly("옵셔널 체이닝", "널 병합", "전개", "OR");
        assertThat(result.getAnswer()).isEqualTo("옵셔널 체이닝");
        assertThat(result.getExplain()).isEqualTo("?. 는 옵셔널 체이닝이다"); // 해설도 함께 저장/응답
        verify(creditUsageService).checkAndConsume(USER_ID, "claude-haiku-4-5"); // 카드를 만든 모델 그대로
    }

    @Test
    void 정답_제출이면_정답수가_오르고_신호등이_승급한다() {
        LearningCard card = cardOwnedBy(USER_ID);
        card.recordCorrect();
        card.recordCorrect(); // 정답 2회 (아직 RED)
        when(learningCardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
        when(learningCardQuizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz(CARD_ID, "정답")));

        QuizSubmitResultDTO result = service.submitQuiz(USER_ID, CARD_ID, QUIZ_ID, " 정답 "); // 공백은 trim

        assertThat(result.isCorrect()).isTrue();
        assertThat(result.getCorrectCount()).isEqualTo(3);
        assertThat(result.getGrade()).isEqualTo("YELLOW"); // 3회 → 노랑
        verify(retentionService).recordSubmission(USER_ID); // 제출 시 streak 갱신
    }

    @Test
    void 오답_제출은_등급을_올리지_않는다() {
        LearningCard card = cardOwnedBy(USER_ID);
        when(learningCardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
        when(learningCardQuizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz(CARD_ID, "정답")));

        QuizSubmitResultDTO result = service.submitQuiz(USER_ID, CARD_ID, QUIZ_ID, "오답");

        assertThat(result.isCorrect()).isFalse();
        assertThat(result.getCorrectCount()).isZero();
        assertThat(result.getGrade()).isEqualTo("RED");
    }

    @Test
    void 다른_카드의_퀴즈_제출이면_LEARNING_QUIZ_NOT_FOUND() {
        when(learningCardRepository.findById(CARD_ID)).thenReturn(Optional.of(cardOwnedBy(USER_ID)));
        when(learningCardQuizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz(999L, "정답")));

        assertThatThrownBy(() -> service.submitQuiz(USER_ID, CARD_ID, QUIZ_ID, "정답"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEARNING_QUIZ_NOT_FOUND);
    }

    @Test
    void 히스토리는_카드생성과_승급지점을_돌려준다() {
        LearningCard card = cardOwnedBy(USER_ID);
        setField(card, "createdAt", java.time.LocalDateTime.of(2026, 7, 10, 9, 0));
        when(learningCardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
        when(learningCardQuizRepository.findByCardId(CARD_ID)).thenReturn(List.of(quiz(CARD_ID, "정답")));

        QuizSubmission sub = QuizSubmission.create(QUIZ_ID, USER_ID, "정답", true, "YELLOW");
        setField(sub, "submittedAt", java.time.LocalDateTime.of(2026, 7, 11, 12, 0));
        when(quizSubmissionRepository.findByUserIdAndQuizIdInOrderBySubmittedAtAsc(eq(USER_ID), any()))
                .thenReturn(List.of(sub));

        List<CardHistoryResponseDTO> history = service.getHistory(USER_ID, CARD_ID);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getNote()).isEqualTo("카드 생성");
        assertThat(history.get(0).getGrade()).isEqualTo("RED");
        assertThat(history.get(1).getGrade()).isEqualTo("YELLOW");
        assertThat(history.get(1).getDate()).isEqualTo("2026-07-11");
    }

    @Test
    void 지난_문제_보기는_내답_정답_해설을_함께_돌려준다() {
        when(learningCardRepository.findById(CARD_ID)).thenReturn(Optional.of(cardOwnedBy(USER_ID)));
        when(learningCardQuizRepository.findByCardId(CARD_ID)).thenReturn(List.of(quiz(CARD_ID, "정답")));

        QuizSubmission sub = QuizSubmission.create(QUIZ_ID, USER_ID, "내가 낸 답", false, "RED");
        setField(sub, "submittedAt", java.time.LocalDateTime.of(2026, 7, 12, 9, 0));
        when(quizSubmissionRepository.findByUserIdAndQuizIdInOrderBySubmittedAtDesc(eq(USER_ID), any()))
                .thenReturn(List.of(sub));

        List<QuizSubmissionResponseDTO> result = service.getSubmissions(USER_ID, CARD_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSubmittedAnswer()).isEqualTo("내가 낸 답");
        assertThat(result.get(0).getAnswer()).isEqualTo("정답");
        assertThat(result.get(0).getExplain()).isEqualTo("해설");
        assertThat(result.get(0).isCorrect()).isFalse();
        assertThat(result.get(0).getSubmittedAt()).isEqualTo("2026-07-12");
    }
}
