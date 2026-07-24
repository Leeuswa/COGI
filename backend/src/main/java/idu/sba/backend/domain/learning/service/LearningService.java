package idu.sba.backend.domain.learning.service;

import idu.sba.backend.domain.learning.dto.CardHistoryResponseDTO;
import idu.sba.backend.domain.learning.dto.CourseRecommendationResponseDTO;
import idu.sba.backend.domain.learning.dto.LearningCardCreateRequestDTO;
import idu.sba.backend.domain.learning.dto.LearningCardResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizSubmissionResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizSubmitResultDTO;
import idu.sba.backend.domain.learning.dto.WeaknessStatResponseDTO;
import java.util.List;

public interface LearningService {
    // 약점 패턴 통계 조회 — 데이터 없으면 빈 목록(콜드스타트)
    List<WeaknessStatResponseDTO> getWeaknessStats(Long userId);
    // 약점별 강의 추천  — 약점 통계를 인프런/Udemy 검색 딥링크로 변환
    List<CourseRecommendationResponseDTO> getCourseRecommendations(Long userId);
    // 학습카드 생성 (API-043) — AI 호출 + 크레딧 소모
    LearningCardResponseDTO createCard(Long userId, LearningCardCreateRequestDTO request);
    // 보관함 목록
    List<LearningCardResponseDTO> getCards(Long userId);
    // 학습카드 상세 (API-044)
    LearningCardResponseDTO getCard(Long userId, Long cardId);
    // 북마크 토글 (API-044-1)
    void toggleBookmark(Long userId, Long cardId, boolean bookmarked);
    // 완료 체크 토글 (API-044-2)
    void toggleComplete(Long userId, Long cardId, boolean completed);
    // 퀴즈 생성 (API-045) — AI 호출 + 크레딧 소모
    QuizResponseDTO createQuiz(Long userId, Long cardId, String questionType);
    // 퀴즈 제출·채점 (API-046) — 정답이면 신호등 승급
    QuizSubmitResultDTO submitQuiz(Long userId, Long cardId, Long quizId, String answer);
    // 승급 히스토리 (API-047) — quiz_submissions에서 유도
    List<CardHistoryResponseDTO> getHistory(Long userId, Long cardId);
    // 지난 문제 보기 — 내가 푼 문제 + 내 답 + 정답 + 해설 (최근순)
    List<QuizSubmissionResponseDTO> getSubmissions(Long userId, Long cardId);
}