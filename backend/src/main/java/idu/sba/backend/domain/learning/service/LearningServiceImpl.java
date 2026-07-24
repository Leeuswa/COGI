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

    // (카테고리 코드, 언어)로 이슈를 묶는 집계 키 — 언어는 null 가능
    private record StatKey(String category, String language) {}

    // 카드 생성 AI 응답 파싱용 — 프롬프트가 이 형태로 답하도록 지시한다
    private record CardContent(String conceptContent, String badExample, String goodExample, List<String> keyPoints) {}

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
    public List<CourseRecommendationResponseDTO> getCourseRecommendations(Long userId) {
        // 약점 집계를 그대로 재사용 → 약점별 딥링크로 변환 (약점 없으면 빈 목록)
        return getWeaknessStats(userId).stream()
                .map(CourseRecommendationResponseDTO::of)
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
        AiGenerationResult result = aiReviewClient.generate(model,
                buildCardPrompt(request.getLevel()), buildCardInput(request));

        // 벤더 호출은 실제로 일어나 비용이 났으므로 사용량을 기록
        aiUsageLogRepository.save(AiUsageLog.of(userId, modelName,
                result.inputTokens(), result.outputTokens(), result.cost(), REQUEST_TYPE_CARD));

        CardContent content = parseCard(result.content());
        LearningCard card = LearningCard.create(userId, request.getCategory(), request.getLevel(), request.getLanguage(),
                modelName, content.conceptContent(), content.badExample(), content.goodExample(),
                content.keyPoints() == null ? null : String.join("\n", content.keyPoints()));
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
    public QuizResponseDTO createQuiz(Long userId, Long cardId, String questionType) {
        LearningCard card = requireOwnedCard(userId, cardId);

        // 퀴즈는 이 카드를 만들 때 쓴 모델 그대로 생성한다. 크레딧도 그 모델 기준으로 소모(실패 시 롤백으로 복구)
        String modelName = card.getModelName();
        creditUsageService.checkAndConsume(userId, modelName);

        AiModel model = resolveAiModel(modelName);
        AiGenerationResult result = aiReviewClient.generate(model,
                buildQuizPrompt(questionType), buildQuizInput(card));
        aiUsageLogRepository.save(AiUsageLog.of(userId, modelName,
                result.inputTokens(), result.outputTokens(), result.cost(), REQUEST_TYPE_CARD));

        QuizContent qc = parseQuiz(result.content());
        String options = (qc.options() == null || qc.options().isEmpty()) ? null : String.join("\n", qc.options());
        LearningCardQuiz quiz = LearningCardQuiz.create(cardId, questionType, qc.question(), options, qc.answer(), qc.explain());
        learningCardQuizRepository.save(quiz);

        return QuizResponseDTO.of(quiz);
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

    // 카드 생성 시스템 프롬프트 — 수준에 맞춰 JSON 한 덩어리로만 답하게 지시
    private String buildCardPrompt(String level) {
        return """
                너는 개발 학습카드를 만드는 튜터야. 주어진 약점 카테고리로 학습카드 한 장을 만들어.
                학습자 수준: %s (수준에 맞게 설명 난이도를 조절)
                아래 JSON 형식으로만 답해. 다른 말이나 코드펜스 없이 JSON만:
                {"conceptContent":"개념 설명(한국어)","badExample":"문제 있는 예시 코드","goodExample":"고친 예시 코드","keyPoints":["핵심1","핵심2","핵심3"]}
                """.formatted(level == null ? "BEGINNER" : level);
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

    // 퀴즈 생성 시스템 프롬프트 — 유형에 맞춰 JSON 한 덩어리로만 답하게 지시
    private String buildQuizPrompt(String questionType) {
        return """
                너는 개발 학습 퀴즈를 만드는 튜터야. 아래 학습 주제로 %s 유형 문제 하나를 만들어.
                MULTIPLE_CHOICE는 보기 4개, OX는 보기 ["O","X"], SHORT_ANSWER·FILL_BLANK는 보기 없이(options는 [] 또는 생략).
                explain에는 왜 그 답이 정답인지 한두 문장 해설을 꼭 넣어(정답을 맞혀도 학습용으로 보여준다).
                아래 JSON 형식으로만 답해. 다른 말이나 코드펜스 없이 JSON만:
                {"question":"문제(한국어)","options":["보기1","보기2"],"answer":"정답","explain":"해설"}
                """.formatted(questionType);
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