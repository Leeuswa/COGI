package idu.sba.backend.domain.learning.service;

import idu.sba.backend.domain.learning.dto.AiSkillResponseDTO;
import idu.sba.backend.domain.learning.dto.CardHistoryResponseDTO;
import idu.sba.backend.domain.learning.dto.CourseRecommendationResponseDTO;
import idu.sba.backend.domain.learning.dto.LearningCardCreateRequestDTO;
import idu.sba.backend.domain.learning.dto.LearningCardResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizSubmissionResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizSubmitResultDTO;
import idu.sba.backend.domain.learning.dto.SkillByWeaknessItemDTO;
import idu.sba.backend.domain.learning.dto.SkillByWeaknessResponseDTO;
import idu.sba.backend.domain.learning.dto.SkillRecommendRequestDTO;

import idu.sba.backend.domain.learning.dto.WeaknessStatResponseDTO;
import idu.sba.backend.domain.learning.entity.AiSkill;
import idu.sba.backend.domain.learning.entity.AiSkillFavorite;
import idu.sba.backend.domain.learning.entity.AiSkillRecommendation;
import idu.sba.backend.domain.learning.entity.LearningCard;
import idu.sba.backend.domain.learning.entity.LearningCardQuiz;
import idu.sba.backend.domain.learning.entity.QuizSubmission;
import idu.sba.backend.domain.learning.entity.WeaknessStat;
import idu.sba.backend.domain.learning.repository.AiSkillFavoriteRepository;
import idu.sba.backend.domain.learning.repository.AiSkillRecommendationRepository;
import idu.sba.backend.domain.learning.repository.AiSkillRepository;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningServiceImpl implements LearningService {

    private static final int MIN_OCCURRENCE = 3; // 3회 이상만 약점으로 집계 (FR-56)
    private static final String REQUEST_TYPE_CARD = "LEARNING_CARD"; // ai_usage_logs 구분값
    private static final String REQUEST_TYPE_STUDY_PLAN = "STUDY_PLAN"; // 학습 계획 호출은 따로 집계
    private static final String REQUEST_TYPE_SKILL_RECOMMEND = "SKILL_RECOMMEND"; // 스킬 추천 호출도 따로 집계 (API-049)
    private static final String KIND_FREE_TEXT = "FREE_TEXT"; // 자유 입력 추천. 지금은 약점 기반과 같은 JSON 스키마다
    private static final String KIND_BY_WEAKNESS = "BY_WEAKNESS"; // 약점 기반 추천(JSON). latest가 kind로 골라 꺼낸다
    private static final int SKILL_RECOMMEND_BY_WEAKNESS_CREDIT = 2; // 약점 기반 추천은 모델 등급이 아니라 고정 2크레딧

    // AI 응답 전용 리더. 날 줄바꿈과 모르는 키를 눈감아 준다 — 이유는 parseAi 주석에 있다.
    // 주입받는 공용 ObjectMapper는 API 응답 직렬화에도 쓰여서 여기 규칙을 얹으면 안 된다
    private static final JsonMapper LENIENT_JSON = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

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
    private final AiSkillRepository aiSkillRepository;
    private final AiSkillFavoriteRepository aiSkillFavoriteRepository;
    private final AiSkillRecommendationRepository aiSkillRecommendationRepository;

    // (카테고리 코드, 언어)로 이슈를 묶는 집계 키 — 언어는 null 가능
    private record StatKey(String category, String language) {}

    // 카드 생성 AI 응답 파싱용 — card_common.txt의 출력 스키마와 키 이름이 1:1로 맞아야 한다
    private record CardContent(String conceptContent, String whyItMatters, String badExample, String goodExample,
                               String diffExplain, List<String> keyPoints, List<String> pitfalls,
                               List<String> selfCheck) {}

    // 퀴즈 생성 AI 응답 파싱용
    private record QuizContent(String question, List<String> options, String answer, String explain) {}

    // 약점 기반 스킬 추천 AI 응답 파싱용 — skill_recommend_by_weakness.txt의 출력 스키마와 키 이름이 1:1로 맞아야 한다
    // AI가 실제로 주는 모양만 담는다. 응답 DTO(SkillByWeaknessItemDTO)에는 skillId·isFavorite가 더 붙어 있어서
    // 그걸로 바로 파싱하면 wire 스키마와 어긋난다. 파싱용과 응답용을 갈라 둔다
    private record RawSkillItem(String provider, String title, String why, String howTo, String prompt) {}

    private record SkillItemsContent(List<RawSkillItem> items) {}

    // 조회할 때마다 집계해서 그대로 돌려준다. weakness_stats에 쓰지 않는다.
    // 예전엔 GET마다 deleteByUserId 후 재삽입을 했는데, 화면 두 곳(약점·스킬추천)이나
    // StrictMode가 동시에 부르면 뒤 트랜잭션이 이미 지운 행을 지우려다 500이 났다.
    // 그 표를 읽는 코드가 어디에도 없어서(응답은 여기서 바로 만든다) 쓰기를 걷어냈다.
    @Override
    @Transactional(readOnly = true)
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

        // 콜드스타트면 빈 목록 → 프론트가 안내문/버튼 처리
        return stats.stream().map(WeaknessStatResponseDTO::of).toList();
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
        String modelName = defaultModelName(userId);

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
    public LearningCardResponseDTO createStudyPlan(Long userId, Long cardId, int days) {
        LearningCard card = requireOwnedCard(userId, cardId);

        // 계획은 카드를 만든 모델로 짠다. 퀴즈와 달리 따로 고르게 하지 않았다
        String modelName = card.getModelName();
        creditUsageService.checkAndConsume(userId, modelName);

        AiModel model = resolveAiModel(modelName);
        AiGenerationResult result = aiReviewClient.generate(model,
                promptBuilder.buildStudyPlan(modelName), buildStudyPlanInput(card, days));
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

    @Override
    @Transactional
    public void toggleSkillFavorite(Long userId, Long skillId) {
        if (aiSkillFavoriteRepository.existsByUserIdAndSkillId(userId, skillId)) {
            aiSkillFavoriteRepository.deleteByUserIdAndSkillId(userId, skillId);
        } else {
            aiSkillFavoriteRepository.save(AiSkillFavorite.of(userId, skillId));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiSkillResponseDTO> getAiSkills(Long userId, String provider) {
        // 스킬마다 조회하면 N+1이라 찜한 id를 먼저 모아두고 목록에 별을 칠한다
        Set<Long> favoriteIds = aiSkillFavoriteRepository.findByUserId(userId).stream()
                .map(AiSkillFavorite::getSkillId)
                .collect(Collectors.toSet());

        return aiSkillRepository.findByProviderForUser(provider, userId).stream()
                .map(skill -> AiSkillResponseDTO.of(skill, favoriteIds.contains(skill.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiSkillResponseDTO> getFavoriteSkills(Long userId) {
        // 내가 찜한 스킬 id로 바로 조회 — provider는 안 가린다. 빈 목록이면 findAllById도 빈 걸 준다
        List<Long> favoriteIds = aiSkillFavoriteRepository.findByUserId(userId).stream()
                .map(AiSkillFavorite::getSkillId)
                .toList();
        return aiSkillRepository.findAllById(favoriteIds).stream()
                .map(skill -> AiSkillResponseDTO.of(skill, true)) // 여기 나온 건 다 내가 찜한 것
                .toList();
    }

    @Override
    @Transactional
    public SkillByWeaknessResponseDTO recommendSkill(Long userId, SkillRecommendRequestDTO request) {
        // 빈 입력으로 AI를 부르면 크레딧만 날아간다. 여기서 끊는다
        String input = request.getWeaknessOrRequirement();
        if (input == null || input.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 카드 생성과 같은 패턴 — 플랜 기본 모델로 크레딧부터 체크·소모
        String modelName = defaultModelName(userId);
        creditUsageService.checkAndConsume(userId, modelName);

        AiModel model = resolveAiModel(modelName);
        AiGenerationResult result = aiReviewClient.generate(model,
                promptBuilder.buildSkillRecommend(modelName), input);
        aiUsageLogRepository.save(AiUsageLog.of(userId, modelName,
                result.inputTokens(), result.outputTokens(), result.cost(), REQUEST_TYPE_SKILL_RECOMMEND));

        // 예전엔 AI가 준 글을 통째로 내려보냈다. 그러면 화면이 문단 하나를 그대로 뿌리는 수밖에 없어서
        // 복사할 프롬프트도 즐겨찾기할 대상도 없고, 원문이 한 글자라도 깨지면 그대로 보였다.
        // 약점 기반 추천과 같은 JSON 스키마로 받아 같은 항목 카드로 만든다.
        // 파싱이 깨지면 여기서 던져 트랜잭션 롤백으로 크레딧까지 복구된다.
        List<SkillByWeaknessItemDTO> items = persistGenerated(
                userId, parseSkillItems(result.content()), List.of());

        aiSkillRecommendationRepository.save(AiSkillRecommendation.of(
                userId, input, result.content(), KIND_FREE_TEXT));

        // 자유 입력이라 근거가 된 약점 카테고리는 없다
        return new SkillByWeaknessResponseDTO(List.of(), items);
    }

    @Override
    @Transactional
    public SkillByWeaknessResponseDTO recommendSkillByWeakness(Long userId) {
        // 약점이 하나도 없으면 AI를 부르기 전에 끊는다 — 크레딧을 쓸 이유가 없다 (콜드스타트 가드)
        List<WeaknessStatResponseDTO> weaknesses = getWeaknessStats(userId);
        if (weaknesses.isEmpty()) {
            throw new BusinessException(ErrorCode.WEAKNESS_STATS_EMPTY);
        }

        // 자유 입력 버전(recommendSkill)과 달리 모델 등급이 아니라 고정 2크레딧을 쓴다
        creditUsageService.checkAndConsumeFixed(userId, SKILL_RECOMMEND_BY_WEAKNESS_CREDIT);

        // 카드 생성과 같은 방식으로 플랜 기본 모델을 쓴다
        String modelName = defaultModelName(userId);
        AiModel model = resolveAiModel(modelName);

        // 사용자가 입력하는 게 없어서 약점 통계를 서버가 직접 요약해 입력으로 넘긴다
        String weaknessSummary = buildWeaknessSummaryInput(weaknesses);
        AiGenerationResult result = aiReviewClient.generate(model,
                promptBuilder.buildSkillRecommendByWeakness(modelName), weaknessSummary);
        aiUsageLogRepository.save(AiUsageLog.of(userId, modelName,
                result.inputTokens(), result.outputTokens(), result.cost(), REQUEST_TYPE_SKILL_RECOMMEND));

        // 파싱이 깨지면 여기서 던져서 트랜잭션 롤백으로 크레딧까지 복구된다
        List<SkillByWeaknessItemDTO> parsed = parseSkillItems(result.content());

        List<String> categories = weaknesses.stream()
                .map(WeaknessStatResponseDTO::getCategory).distinct().toList();

        // 화면에만 있으면 즐겨찾기를 못 단다. 내 소유 스킬 행으로 남기고 그 id를 응답에 실어 보낸다
        List<SkillByWeaknessItemDTO> items = persistGenerated(userId, parsed, categories);

        // 이력은 기존 엔티티 재사용 — inputText는 서버가 만든 약점 요약, recommendationResult는 AI 원문(JSON)
        aiSkillRecommendationRepository.save(AiSkillRecommendation.of(
                userId, weaknessSummary, result.content(), KIND_BY_WEAKNESS));

        return new SkillByWeaknessResponseDTO(categories, items);
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
        return parseAi(content, CardContent.class, "카드");
    }

    // 파싱이 되기도 하고 안 되기도 하던 자리. 원인은 두 가지였다.
    // 1) 프롬프트가 question·explain 안에 코드블록을 넣으라고 시키는데 모델이 줄바꿈을 그대로 흘린다.
    //    JSON 문자열 안의 날 줄바꿈은 위법이라 코드블록이 든 문제만 골라서 터졌다 → 관대한 리더로 받는다
    // 2) 모델이 스키마에 없는 키를 하나 얹어도 통째로 실패했다 → 모르는 키는 버린다
    private <T> T parseAi(String content, Class<T> type, String what) {
        try {
            return LENIENT_JSON.readValue(content, type);
        } catch (Exception e) {
            // 원문을 안 남기면 다음에도 "어쩔 땐 되고 어쩔 땐 안 된다"로만 보인다
            log.error("AI 응답 파싱 실패 - 대상={}, 원문={}", what, abbreviate(content), e);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED);
        }
    }

    // 로그에 응답 전체를 쏟으면 읽을 수가 없다. 앞부분만 남겨도 뭐가 왔는지는 보인다
    private String abbreviate(String content) {
        if (content == null) return "(없음)";
        return content.length() <= 800 ? content : content.substring(0, 800) + "…(생략)";
    }

    // 퀴즈 생성 입력 — 어떤 카드(개념)로 문제를 낼지 넘긴다
    private String buildQuizInput(LearningCard card) {
        return "주제: " + card.getCategory()
                + (card.getLanguage() != null && !card.getLanguage().isBlank() ? "\n언어: " + card.getLanguage() : "")
                + "\n개념: " + card.getConceptContent();
    }

    private QuizContent parseQuiz(String content) {
        return parseAi(content, QuizContent.class, "퀴즈");
    }

    // 플랜의 기본 모델 — allowed_models의 첫 값이 그 플랜 기본이라는 규칙을 한 곳에만 둔다
    private String defaultModelName(Long userId) {
        return subscriptionService.getCurrentPlanEntity(userId).getAllowedModels().split(",")[0].trim();
    }

    // 약점 통계를 AI 입력으로 요약 — 카테고리·언어·발생 횟수를 한 줄씩 나열한다
    // AI가 만들어 준 항목을 내 소유 스킬 행으로 남긴다. 그래야 별을 달 수 있다.
    // 같은 provider·title이 이미 있으면 새로 만들지 않고 내용만 갈아끼운다 — 누를 때마다 쌓이면 안 된다
    private List<SkillByWeaknessItemDTO> persistGenerated(Long userId, List<SkillByWeaknessItemDTO> items,
                                                          List<String> categories) {
        if (items == null) {
            return List.of();
        }
        String category = categories.isEmpty() ? null : categories.get(0); // 근거가 된 약점 중 첫 번째
        Set<Long> favoriteIds = aiSkillFavoriteRepository.findByUserId(userId).stream()
                .map(AiSkillFavorite::getSkillId)
                .collect(Collectors.toSet());

        List<SkillByWeaknessItemDTO> saved = new ArrayList<>();
        for (SkillByWeaknessItemDTO item : items) {
            AiSkill skill = aiSkillRepository
                    .findFirstByUserIdAndProviderAndTitle(userId, item.provider(), item.title())
                    .orElseGet(() -> aiSkillRepository.save(AiSkill.ofGenerated(
                            userId, item.provider(), category, item.title(), item.why(), item.howTo(), item.prompt())));
            skill.updateGenerated(category, item.why(), item.howTo(), item.prompt()); // 기존 행이면 더티체킹으로 갱신
            saved.add(item.withSkill(skill.getId(), favoriteIds.contains(skill.getId())));
        }
        return saved;
    }

    // 마지막 추천을 그대로 다시 꺼낸다. 크레딧도 AI 호출도 없다.
    // 두 종류를 따로 꺼낸다 — 약점 기반(BY_WEAKNESS)과 자유 입력(FREE_TEXT)은 한 화면에 같이 떠 있고,
    // 섞어서 최신 하나만 주면 다른 쪽 결과가 화면을 나갔다 오는 순간 사라진다.
    // readOnly가 아닌 이유 — 옛 이력엔 스킬 행이 없어서 이때 만들어 줘야 별을 달 수 있다
    @Override
    @Transactional
    public SkillByWeaknessResponseDTO getLatestSkillRecommendation(Long userId, String kind) {
        String target = KIND_FREE_TEXT.equals(kind) ? KIND_FREE_TEXT : KIND_BY_WEAKNESS;
        AiSkillRecommendation last = aiSkillRecommendationRepository
                .findTopByUserIdAndKindOrderByCreatedAtDesc(userId, target)
                .orElse(null);
        if (last == null) {
            return null; // 한 번도 안 했으면 화면이 패널을 안 그린다
        }
        try {
            // 자유 입력은 inputText가 사용자가 쓴 문장이라 카테고리를 되짚을 게 없다.
            // 그대로 categoriesOf에 넣으면 그 문장이 약점 이름인 척 화면에 뜬다
            List<String> categories = KIND_BY_WEAKNESS.equals(target) ? categoriesOf(last) : List.of();
            // 즐겨찾기는 저장값을 재활용하지 않고 지금 값으로 다시 계산한다. 그 사이 별을 껐을 수 있다
            List<SkillByWeaknessItemDTO> items = persistGenerated(
                    userId, parseSkillItems(last.getRecommendationResult()), categories);
            return new SkillByWeaknessResponseDTO(categories, items);
        } catch (Exception e) {
            // 이력 한 줄이 깨졌다고 화면을 막지 않는다. 없는 것처럼 넘긴다
            log.warn("지난 추천을 복원하지 못했습니다 - userId={}, kind={}", userId, target, e);
            return null;
        }
    }

    // 이력에 남긴 약점 요약 문자열에서 카테고리만 되짚는다 ("카테고리: BUG, 언어: Java, 발생 횟수: 3회" 줄들)
    private List<String> categoriesOf(AiSkillRecommendation recommendation) {
        return Arrays.stream(recommendation.getInputText().split("\n"))
                .map(line -> line.replaceFirst("^카테고리:\\s*", "").split(",")[0].trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String buildWeaknessSummaryInput(List<WeaknessStatResponseDTO> weaknesses) {
        return weaknesses.stream()
                .map(w -> "카테고리: " + w.getCategory()
                        + (w.getLanguage() != null && !w.getLanguage().isBlank() ? ", 언어: " + w.getLanguage() : "")
                        + ", 발생 횟수: " + w.getOccurrenceCount() + "회")
                .collect(Collectors.joining("\n"));
    }

    // AI 원문 → 응답 DTO 목록. items 키로 감싸 오는 게 정상이지만 배열만 던지는 모델도 있어 둘 다 받는다
    private List<SkillByWeaknessItemDTO> parseSkillItems(String content) {
        String trimmed = content == null ? "" : content.trim();
        List<RawSkillItem> raw = trimmed.startsWith("[")
                ? List.of(parseAi(trimmed, RawSkillItem[].class, "스킬 추천(배열)"))
                : parseAi(trimmed, SkillItemsContent.class, "스킬 추천").items();
        if (raw == null || raw.isEmpty()) {
            log.error("AI 응답에 items가 비어 있습니다 - 원문={}", abbreviate(content));
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED);
        }
        return raw.stream()
                .map(r -> SkillByWeaknessItemDTO.parsed(r.provider(), r.title(), r.why(), r.howTo(), r.prompt()))
                .toList();
    }

    // 학습 계획 입력 — 기간이 단계 수를 정하고, 등급은 무엇을 채울지를 정한다 (study_plan.txt 규칙)
    private String buildStudyPlanInput(LearningCard card, int days) {
        return "계획 기간: " + days + "일"
                + "\n시작 날짜: " + LocalDate.now() // 실제 날짜로 계획을 세우게 오늘을 알려준다
                + "\n주제: " + card.getCategory()
                + "\n현재 등급: " + card.getGrade()
                + "\n누적 정답: " + card.getCorrectCount() + "회"
                + "\n개념: " + card.getConceptContent();
    }

    // 계획 JSON에서 steps 배열만 떼어 저장한다. 프론트가 배열을 바로 받도록 응답에 그대로 실린다
    private String parseStudyPlanSteps(String content) {
        try {
            return LENIENT_JSON.readTree(content).get("steps").toString();
        } catch (Exception e) { // steps 키가 없거나 JSON이 깨졌으면 크레딧까지 롤백
            log.error("AI 응답 파싱 실패 - 대상=학습계획, 원문={}", abbreviate(content), e);
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