package idu.sba.backend.domain.learning.controller;

import idu.sba.backend.domain.learning.dto.CardHistoryResponseDTO;
import idu.sba.backend.domain.learning.dto.LearningCardBookmarkRequestDTO;
import idu.sba.backend.domain.learning.dto.LearningCardCompleteRequestDTO;
import idu.sba.backend.domain.learning.dto.LearningCardCreateRequestDTO;
import idu.sba.backend.domain.learning.dto.LearningCardResponseDTO;
import idu.sba.backend.domain.learning.dto.PlanDateResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizCreateRequestDTO;
import idu.sba.backend.domain.learning.dto.QuizResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizSubmissionResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizSubmitRequestDTO;
import idu.sba.backend.domain.learning.dto.QuizSubmitResultDTO;
import idu.sba.backend.domain.learning.service.LearningService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/learning-cards")
@RequiredArgsConstructor
public class LearningCardController {

    private final LearningService learningService;

    // API-043: 약점 카테고리로 AI 학습카드 생성 (크레딧 소진 시 402)
    @PostMapping
    public ApiResponse<LearningCardResponseDTO> create(@AuthenticationPrincipal Long userId,
                                                       @RequestBody LearningCardCreateRequestDTO request) {
        return ApiResponse.ok(learningService.createCard(userId, request));
    }

    // 보관함 목록 (프론트 Cards.tsx가 쓰는 목록 조회)
    @GetMapping
    public ApiResponse<List<LearningCardResponseDTO>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(learningService.getCards(userId));
    }

    // API-044: 학습카드 상세
    @GetMapping("/{cardId}")
    public ApiResponse<LearningCardResponseDTO> detail(@AuthenticationPrincipal Long userId,
                                                       @PathVariable Long cardId) {
        return ApiResponse.ok(learningService.getCard(userId, cardId));
    }

    // API-044-1: 북마크 토글
    @PatchMapping("/{cardId}/bookmark")
    public ApiResponse<Void> bookmark(@AuthenticationPrincipal Long userId, @PathVariable Long cardId,
                                      @RequestBody LearningCardBookmarkRequestDTO request) {
        learningService.toggleBookmark(userId, cardId, request.isBookmarked());
        return ApiResponse.ok("북마크가 변경되었습니다.");
    }

    // API-044-2: 완료 체크 토글
    @PatchMapping("/{cardId}/complete")
    public ApiResponse<Void> complete(@AuthenticationPrincipal Long userId, @PathVariable Long cardId,
                                      @RequestBody LearningCardCompleteRequestDTO request) {
        learningService.toggleComplete(userId, cardId, request.isCompleted());
        return ApiResponse.ok("완료 상태가 변경되었습니다.");
    }

    // API-045: 퀴즈 생성 (크레딧 소진 시 402). 모델은 사용자가 고르고, 안 고르면 카드 모델로 간다
    @PostMapping("/{cardId}/quiz")
    public ApiResponse<QuizResponseDTO> createQuiz(@AuthenticationPrincipal Long userId, @PathVariable Long cardId,
                                                   @RequestBody QuizCreateRequestDTO request) {
        return ApiResponse.ok(learningService.createQuiz(userId, cardId, request.getQuestionType(), request.getModelName()));
    }

    // 학습 계획 생성 — 고른 기간(3·5·7·14일)을 그 기간만큼 채운다. 한 번 만들면 카드에 남아 재조회는 공짜
    @PostMapping("/{cardId}/study-plan")
    public ApiResponse<LearningCardResponseDTO> createStudyPlan(@AuthenticationPrincipal Long userId,
                                                                @PathVariable Long cardId,
                                                                @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(learningService.createStudyPlan(userId, cardId, days));
    }

    // 계획을 달력에 등록 — 오늘이 기준일이 되고 이때부터 대시보드 달력·알림에 뜬다. AI 호출 없음
    @PostMapping("/{cardId}/study-plan/register")
    public ApiResponse<LearningCardResponseDTO> registerStudyPlan(@AuthenticationPrincipal Long userId,
                                                                  @PathVariable Long cardId) {
        return ApiResponse.ok(learningService.registerStudyPlan(userId, cardId));
    }

    // 등록된 계획을 날짜로 펼쳐 준다 — 대시보드 달력이 그 달 구간으로 부른다 (읽기 전용)
    @GetMapping("/plan-dates")
    public ApiResponse<List<PlanDateResponseDTO>> planDates(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(learningService.getPlanDates(userId, from, to));
    }

    // 오늘 할 단계가 있으면 알림을 만든다(같은 단계는 하루 한 번). 조회 API에 쓰기를 섞지 않으려고 POST로 뺐다
    @PostMapping("/plan-notifications/today")
    public ApiResponse<List<PlanDateResponseDTO>> todayPlanNotifications(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(learningService.ensureTodayPlanNotifications(userId));
    }

    // API-046: 퀴즈 제출·채점 (정답이면 신호등 승급)
    @PostMapping("/{cardId}/quiz/{quizId}/submit")
    public ApiResponse<QuizSubmitResultDTO> submitQuiz(@AuthenticationPrincipal Long userId, @PathVariable Long cardId,
                                                       @PathVariable Long quizId, @RequestBody QuizSubmitRequestDTO request) {
        return ApiResponse.ok(learningService.submitQuiz(userId, cardId, quizId, request.getAnswer()));
    }

    // API-047: 승급 히스토리
    @GetMapping("/{cardId}/history")
    public ApiResponse<List<CardHistoryResponseDTO>> history(@AuthenticationPrincipal Long userId,
                                                             @PathVariable Long cardId) {
        return ApiResponse.ok(learningService.getHistory(userId, cardId));
    }

    // 지난 문제 보기 — 내가 푼 문제 + 내 답 + 정답 + 해설 (복습용)
    @GetMapping("/{cardId}/submissions")
    public ApiResponse<List<QuizSubmissionResponseDTO>> submissions(@AuthenticationPrincipal Long userId,
                                                                    @PathVariable Long cardId) {
        return ApiResponse.ok(learningService.getSubmissions(userId, cardId));
    }
}
