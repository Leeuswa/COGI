package idu.sba.backend.domain.learning.controller;

import idu.sba.backend.domain.learning.dto.CardHistoryResponseDTO;
import idu.sba.backend.domain.learning.dto.LearningCardBookmarkRequestDTO;
import idu.sba.backend.domain.learning.dto.LearningCardCompleteRequestDTO;
import idu.sba.backend.domain.learning.dto.LearningCardCreateRequestDTO;
import idu.sba.backend.domain.learning.dto.LearningCardResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizCreateRequestDTO;
import idu.sba.backend.domain.learning.dto.QuizResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizSubmissionResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizSubmitRequestDTO;
import idu.sba.backend.domain.learning.dto.QuizSubmitResultDTO;
import idu.sba.backend.domain.learning.service.LearningService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    // API-045: 퀴즈 생성 (크레딧 소진 시 402)
    @PostMapping("/{cardId}/quiz")
    public ApiResponse<QuizResponseDTO> createQuiz(@AuthenticationPrincipal Long userId, @PathVariable Long cardId,
                                                   @RequestBody QuizCreateRequestDTO request) {
        return ApiResponse.ok(learningService.createQuiz(userId, cardId, request.getQuestionType()));
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
