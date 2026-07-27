package idu.sba.backend.domain.learning.service;

import idu.sba.backend.domain.learning.dto.CardHistoryResponseDTO;
import idu.sba.backend.domain.learning.dto.CourseRecommendationResponseDTO;
import idu.sba.backend.domain.learning.dto.LearningCardCreateRequestDTO;
import idu.sba.backend.domain.learning.dto.LearningCardResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizSubmissionResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizSubmitResultDTO;
import idu.sba.backend.domain.learning.dto.WeaknessStatResponseDTO;
import idu.sba.backend.domain.learning.entity.LearningCard;
import idu.sba.backend.domain.learning.entity.LearningCardQuiz;
import idu.sba.backend.domain.learning.entity.QuizSubmission;
import idu.sba.backend.domain.learning.entity.WeaknessStat;
import idu.sba.backend.domain.learning.repository.CourseRepository;
import idu.sba.backend.domain.learning.repository.LearningCardQuizRepository;
import idu.sba.backend.domain.learning.repository.LearningCardRepository;
import idu.sba.backend.domain.learning.repository.QuizSubmissionRepository;
import idu.sba.backend.domain.learning.repository.WeaknessStatRepository;
import idu.sba.backend.domain.payment.entity.Plan;
import idu.sba.backend.domain.payment.service.CreditUsageService;
import idu.sba.backend.domain.payment.service.SubscriptionService;
import idu.sba.backend.domain.review.entity.AiUsageLog;
import idu.sba.backend.domain.review.entity.Review;
import idu.sba.backend.domain.review.entity.ReviewIssue;
import idu.sba.backend.domain.retention.service.RetentionService;
import idu.sba.backend.domain.review.repository.AiUsageLogRepository;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewRepository;
import idu.sba.backend.global.ai.AiGenerationResult;
import idu.sba.backend.global.ai.AiModel;
import idu.sba.backend.global.ai.AiReviewClient;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearningServiceImpl implements LearningService {

    private static final int MIN_OCCURRENCE = 3; // 3회 이상만 약점으로 집계 (FR-56)
    private static final String REQUEST_TYPE_CARD = "LEARNING_CARD"; // ai_usage_logs 구분값
    private static final String REQUEST_TYPE_STUDY_PLAN = "STUDY_PLAN"; // 학습 계획 호출은 따로 집계

    private final ReviewRepository reviewRepository;
    private final ReviewIssueRepository reviewIssueRepository;
    private final WeaknessStatRepository weaknessStatRepository;
    private final LearningCardRepository learningCardRepository;
    private final LearningCardQuizRepository learningCardQuizRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final SubscriptionService subscriptionService;
    private final CreditUsageService creditUsageService;
    private final AiReviewClient aiReviewClient;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final ObjectMapper objectMapper;
    private final RetentionService retentionService;
    private final CourseRepository courseRepository;
    private final LearningPromptBuilder promptBuilder;

    // (카테고리 코드, 언어)로 이슈를 묶는 집계 키 — 언어는 null 가능
    private record StatKey(String category, String language) {}

    // 카드 생성 AI 응답 파싱용 — card_common.txt의 출력 스키마와 키 이름이 1:1로 맞아야 한다
    private record CardContent(String conceptContent, String whyItMatters, String badExample, String goodExample,
                               String diffExplain, List<String> keyPoints, List<String> pitfalls,
                               List<String> selfCheck) {}

    // 퀴즈 생성 AI 응답 파싱용
    private record QuizContent(String question, List<String> options, String answer, String explain) {}

    @Override
    @Transactional
    public List<WeaknessStatResponseDTO> getWeaknessStats(Long userId) {
        // 내 리뷰 전부 조회 (PR/붙여넣기/업로드 포함 — 게스트는 userId가 없어 자동 제외, FR-55)
        List<Review> reviews = reviewRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Long> reviewIds = reviews.stream().map(Review::getId).toList();

        // 리뷰별 언어 매핑 (language가 null 가능이라 toMap 대신 put으로)
        Map<Long, String> languageByReview = new HashMap<>();
        reviews.forEach(review -> languageByReview.put(review.getId(), review.getLanguage()));

        // acknowledged=false(진짜 약점)인 이슈만 (카테고리, 언어)로 묶기
        Map<StatKey, List<ReviewIssue>> grouped = reviewIssueRepository.findByReviewIdIn(reviewIds).stream()
                .filter(issue -> !issue.isAcknowledged())
                .collect(Collectors.groupingBy(issue ->
                        new StatKey(issue.getCategory().name(), languageByReview.get(issue.getReviewId()))));

        // 3회 이상만 통계 행으로 변환 (occurrenceCount=발생 수, updatedAt=마지막 발생 시각)
        List<WeaknessStat> stats = grouped.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= MIN_OCCURRENCE)
                .map(entry -> {
                    StatKey key = entry.getKey();
                    List<ReviewIssue> issues = entry.getValue();
                    LocalDateTime lastOccurred = issues.stream()
                            .map(ReviewIssue::getCreatedAt)
                            .max(Comparator.naturalOrder())
                            .orElse(null);
                    return WeaknessStat.of(userId, key.category(), key.language(), issues.size(), lastOccurred);
                })
                .toList();

        // 기존 통계를 비우고 새로 저장 (조회 시점 최신값으로 갈아끼우기)
        weaknessStatRepository.deleteByUserId(userId);
        List<WeaknessStat> saved = weaknessStatRepository.saveAll(stats);

        // 콜드스타트면 빈 목록 → 프론트가 안내문/버튼 처리
        return saved.stream().map(WeaknessStatResponseDTO::of).toList();
    }

    @Override
    @Transactional
    public List<CourseRecommendationResponseDTO> getCourseRecommendations(Long userId) {
        // 내 약점 → 큐레이션 courses 매칭.
        // 규칙: category 일치 AND (강의 language가 null(언어무관) 또는 약점 언어와 같음)
        // 강의는 여러 약점에 걸쳐도 한 번만 노출(중복 제거)
        List<WeaknessStatResponseDTO> weaknesses = getWeaknessStats(userId);
        if (weaknesses.isEmpty()) {
            return List.of(); // 콜드스타트 → 빈 목록
        }
        List<String> categories = weaknesses.stream()
                .map(WeaknessStatResponseDTO::getCategory).distinct().toList();

        return courseRepository.findByCategoryIn(categories).stream()
                .filter(course -> weaknesses.stream().anyMatch(w ->
                        w.getCategory().equals(course.getCategory())
                                && (course.getLanguage() == null
                                    || course.getLanguage().equalsIgnoreCase(w.getLanguage()))))
                .map(course -> new CourseRecommendationResponseDTO(
                        course.getTitle(), course.getDescription(), course.getUrl(), course.getPlatform()))
                .toList();
    }


    @Override
    @Transactional
    public LearningCardResponseDTO createCard(Long userId, LearningCardCreateRequestDTO request) {
        // 프론트가 모델을 안 보내므로 플랜의 기본(첫 허용) 모델을 쓴다 — 리뷰와 동일하게 플랜별 모델 반영
        Plan plan = subscriptionService.getCurrentPlanEntity(userId);
        String modelName = plan.getAllowedModels().split(",")[0].trim();

        // 모델 검증 뒤 크레딧 소모 — 부족하면 여기서 402. AI 호출/파싱이 실패하면 @Transactional로 이 소모까지 롤백된다
        creditUsageService.checkAndConsume(userId, modelName);

        AiModel model = resolveAiModel(modelName);
        // 수준과 모델 티어에 따라 프롬프트가 갈린다 — 같은 카테고리라도 초급/고급, 1티어/3티어 결과가 달라진다
        AiGenerationResult result = aiReviewClient.generate(model,
                promptBuilder.buildCard(request.getLevel(), modelName), buildCardInput(request));

        // 벤더 호출은 실제로 일어나 비용이 났으므로 사용량을 기록
        aiUsageLogRepository.save(AiUsageLog.of(userId, modelName,
                result.inputTokens(), result.outputTokens(), result.cost(), REQUEST_TYPE_CARD));

        CardContent content = parseCard(result.content());
        LearningCard card = LearningCard.create(userId, request.getCategory(), request.getLevel(), request.getLanguage(),
                modelName, content.conceptContent(), content.whyItMatters(), content.badExample(),
                content.goodExample(), content.diffExplain(), joinLines(content.keyPoints()),
                joinLines(content.pitfalls()), joinLines(content.selfCheck()));
        learningCardRepository.save(card);

        return LearningCardResponseDTO.of(card);
    }

    @Override
    public List<LearningCardResponseDTO> getCards(Long userId) {
        return learningCardRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(LearningCardResponseDTO::of)
                .toList();
    }

    @Override
    public LearningCardResponseDTO getCard(Long userId, Long cardId) {
        return LearningCardResponseDTO.of(requireOwnedCard(userId, cardId));
    }

    @Override
    @Transactional
    public void toggleBookmark(Long userId, Long cardId, boolean bookmarked) {
        requireOwnedCard(userId, cardId).updateBookmark(bookmarked); // 변경은 더티체킹으로 반영
    }

    @Override
    @Transactional
    public void toggleComplete(Long userId, Long cardId, boolean completed) {
        requireOwnedCard(userId, cardId).updateCompleted(completed);
    }

    @Override
    @Transactional
    public QuizResponseDTO createQuiz(Long userId, Long cardId, String questionType, String requestedModel) {
        LearningCard card = requireOwnedCard(userId, cardId);

        // 사용자가 고른 모델을 쓰되 플랜에 없는 모델이면 거부한다. 안 골랐으면 카드 만들 때 쓴 모델로 간다
        String modelName = resolveQuizModel(userId, requestedModel, card.getModelName());
        creditUsageService.checkAndConsume(userId, modelName); // 크레딧도 고른 모델 기준(실패 시 롤백으로 복구)

        AiModel model = resolveAiModel(modelName);
        AiGenerationResult result = aiReviewClient.generate(model,
                promptBuilder.buildQuiz(card.getLevel(), questionType, modelName), buildQuizInput(card));
        aiUsageLogRepository.save(AiUsageLog.of(userId, modelName,
                result.inputTokens(), result.outputTokens(), result.cost(), REQUEST_TYPE_CARD));

        QuizContent qc = parseQuiz(result.content());
        String options = (qc.options() == null || qc.options().isEmpty()) ? null : String.join("\n", qc.options());
        LearningCardQuiz quiz = LearningCardQuiz.create(cardId, questionType, modelName,
                qc.question(), options, qc.answer(), qc.explain());
        learningCardQuizRepository.save(quiz);

        return QuizResponseDTO.of(quiz);
    }

    @Override
    @Transactional
    public LearningCardResponseDTO createStudyPlan(Long userId, Long cardId) {
        LearningCard card = requireOwnedCard(userId, cardId);

        // 계획은 카드를 만든 모델로 짠다. 퀴즈와 달리 따로 고르게 하지 않았다
        String modelName = card.getModelName();
        creditUsageService.checkAndConsume(userId, modelName);

        AiModel model = resolveAiModel(modelName);
        AiGenerationResult result = aiReviewClient.generate(model,
                promptBuilder.buildStudyPlan(modelName), buildStudyPlanInput(card));
        aiUsageLogRepository.save(AiUsageLog.of(userId, modelName,
                result.inputTokens(), result.outputTokens(), result.cost(), REQUEST_TYPE_STUDY_PLAN));

        // 저장 전에 파싱해 형식을 확인한다. 깨졌으면 여기서 롤백돼 크레딧이 돌아간다
        card.updateStudyPlan(parseStudyPlanSteps(result.content()));

        return LearningCardResponseDTO.of(card);
    }

    @Override
    @Transactional
    public QuizSubmitResultDTO submitQuiz(Long userId, Long cardId, Long quizId, String answer) {
        LearningCard card = requireOwnedCard(userId, cardId);
        LearningCardQuiz quiz = learningCardQuizRepository.findById(quizId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEARNING_QUIZ_NOT_FOUND));
        if (!cardId.equals(quiz.getCardId())) { // 다른 카드의 퀴즈면 잘못된 요청
            throw new BusinessException(ErrorCode.LEARNING_QUIZ_NOT_FOUND);
        }

        boolean correct = quiz.getAnswer().trim().equals(answer == null ? "" : answer.trim());
        if (correct) {
            card.recordCorrect(); // 정답만 등급에 반영 (더티체킹으로 저장)
        }
        quizSubmissionRepository.save(QuizSubmission.create(quizId, userId, answer, correct, card.getGrade()));

        // 제출 자체가 리텐션 기준 — 정답 여부와 무관하게 streak 갱신 (RET-001)
        retentionService.recordSubmission(userId);

        return QuizSubmitResultDTO.of(correct, card.getGrade(), card.getCorrectCount());
    }

    @Override
    public List<CardHistoryResponseDTO> getHistory(Long userId, Long cardId) {
        LearningCard card = requireOwnedCard(userId, cardId);

        List<Long> quizIds = learningCardQuizRepository.findByCardId(cardId).stream()
                .map(LearningCardQuiz::getId).toList();
        List<QuizSubmission> submissions = quizIds.isEmpty()
                ? List.of()
                : quizSubmissionRepository.findByUserIdAndQuizIdInOrderBySubmittedAtAsc(userId, quizIds);

        // 카드 생성이 첫 줄, 이후 제출 이력에서 등급이 바뀌는 지점만 승급으로 기록
        List<CardHistoryResponseDTO> history = new ArrayList<>();
        history.add(new CardHistoryResponseDTO(toDate(card.getCreatedAt()), "RED", "카드 생성"));
        String prevGrade = "RED";
        for (QuizSubmission submission : submissions) {
            if (!submission.getGradeAfter().equals(prevGrade)) {
                history.add(new CardHistoryResponseDTO(
                        toDate(submission.getSubmittedAt()), submission.getGradeAfter(), promoteNote(submission.getGradeAfter())));
                prevGrade = submission.getGradeAfter();
            }
        }
        return history;
    }

    @Override
    public List<QuizSubmissionResponseDTO> getSubmissions(Long userId, Long cardId) {
        requireOwnedCard(userId, cardId); // 소유자 검증

        // 카드 퀴즈를 id로 찾아 두고, 제출 이력을 최근순으로 붙여 내려준다
        Map<Long, LearningCardQuiz> quizById = learningCardQuizRepository.findByCardId(cardId).stream()
                .collect(Collectors.toMap(LearningCardQuiz::getId, quiz -> quiz));
        if (quizById.isEmpty()) {
            return List.of();
        }
        return quizSubmissionRepository
                .findByUserIdAndQuizIdInOrderBySubmittedAtDesc(userId, List.copyOf(quizById.keySet())).stream()
                .map(submission -> QuizSubmissionResponseDTO.of(submission, quizById.get(submission.getQuizId())))
                .toList();
    }

    // 카드 조회 + 소유자 검증 (없으면 404, 남의 카드면 403)
    private LearningCard requireOwnedCard(Long userId, Long cardId) {
        LearningCard card = learningCardRepository.findById(cardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEARNING_CARD_NOT_FOUND));
        if (!userId.equals(card.getUserId())) {
            throw new BusinessException(ErrorCode.LEARNING_CARD_ACCESS_DENIED);
        }
        return card;
    }

    // 모델 문자열 → enum (플랜 설정이 어긋난 경우 방어)
    private AiModel resolveAiModel(String modelName) {
        try {
            return AiModel.fromId(modelName);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED);
        }
    }

    // 퀴즈에 쓸 모델 결정 — 안 골랐으면 카드 모델, 골랐으면 내 플랜에 있는지 확인하고 통과시킨다
    private String resolveQuizModel(Long userId, String requestedModel, String cardModel) {
        if (requestedModel == null || requestedModel.isBlank()) {
            return cardModel;
        }
        Plan plan = subscriptionService.getCurrentPlanEntity(userId);
        boolean allowed = Arrays.stream(plan.getAllowedModels().split(","))
                .map(String::trim)
                .anyMatch(requestedModel::equals);
        if (!allowed) { // 상위 플랜 모델을 요청한 경우 — 프론트에서 막지만 서버도 한 번 더 본다
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED);
        }
        return requestedModel;
    }

    // 카드 생성 입력 — 카테고리와 언어(있으면)를 넘긴다
    private String buildCardInput(LearningCardCreateRequestDTO request) {
        String input = "카테고리: " + request.getCategory();
        if (request.getLanguage() != null && !request.getLanguage().isBlank()) {
            input += "\n언어: " + request.getLanguage();
        }
        return input;
    }

    // AI 응답 JSON 파싱 — 형식을 벗어나면 AI_MODEL_CALL_FAILED (트랜잭션 롤백으로 크레딧도 복구)
    private CardContent parseCard(String content) {
        try {
            return objectMapper.readValue(content, CardContent.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED);
        }
    }

    // 퀴즈 생성 입력 — 어떤 카드(개념)로 문제를 낼지 넘긴다
    private String buildQuizInput(LearningCard card) {
        return "주제: " + card.getCategory()
                + (card.getLanguage() != null && !card.getLanguage().isBlank() ? "\n언어: " + card.getLanguage() : "")
                + "\n개념: " + card.getConceptContent();
    }

    private QuizContent parseQuiz(String content) {
        try {
            return objectMapper.readValue(content, QuizContent.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED);
        }
    }

    // 학습 계획 입력 — 등급과 누적 정답 수를 같이 넘겨야 AI가 간격을 조절한다 (study_plan.txt 1번 규칙)
    private String buildStudyPlanInput(LearningCard card) {
        return "주제: " + card.getCategory()
                + "\n현재 등급: " + card.getGrade()
                + "\n누적 정답: " + card.getCorrectCount() + "회"
                + "\n개념: " + card.getConceptContent();
    }

    // 계획 JSON에서 steps 배열만 떼어 저장한다. 프론트가 배열을 바로 받도록 응답에 그대로 실린다
    private String parseStudyPlanSteps(String content) {
        try {
            return objectMapper.readTree(content).get("steps").toString();
        } catch (Exception e) { // steps 키가 없거나 JSON이 깨졌으면 크레딧까지 롤백
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED);
        }
    }

    // 목록형 응답을 줄바꿈으로 이어 한 컬럼에 담는다 (keyPoints·pitfalls·selfCheck 공통)
    private String joinLines(List<String> values) {
        return (values == null || values.isEmpty()) ? null : String.join("\n", values);
    }

    // 승급 등급별 히스토리 문구
    private String promoteNote(String grade) {
        return switch (grade) {
            case "YELLOW" -> "노랑 승급";
            case "GREEN" -> "초록 승급 (해결)";
            case "GREEN_PLUS" -> "해결+ 달성";
            default -> "등급 변경";
        };
    }

    private String toDate(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}