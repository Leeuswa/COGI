package idu.sba.backend.domain.learning.service;

import idu.sba.backend.domain.learning.dto.AiSkillResponseDTO;
import idu.sba.backend.domain.learning.dto.CardHistoryResponseDTO;
import idu.sba.backend.domain.learning.dto.CourseRecommendationResponseDTO;
import idu.sba.backend.domain.learning.dto.LearningCardCreateRequestDTO;
import idu.sba.backend.domain.learning.dto.LearningCardResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizSubmissionResponseDTO;
import idu.sba.backend.domain.learning.dto.QuizSubmitResultDTO;
import idu.sba.backend.domain.learning.dto.SkillByWeaknessResponseDTO;
import idu.sba.backend.domain.learning.dto.SkillRecommendRequestDTO;
import idu.sba.backend.domain.learning.dto.SkillRecommendResponseDTO;
import idu.sba.backend.domain.learning.dto.WeaknessStatResponseDTO;

import java.util.List;

public interface LearningService {

    // 약점 패턴 통계 조회 — 데이터 없으면 빈 목록(콜드스타트)
    List<WeaknessStatResponseDTO> getWeaknessStats(Long userId);

    // 약점별 강의 추천 — 약점 통계를 인프런/Udemy 검색 딥링크로 변환
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

    // 퀴즈 생성 (API-045) — AI 호출 + 크레딧 소모. modelName은 사용자가 고른 모델(없으면 카드 모델)
    QuizResponseDTO createQuiz(Long userId, Long cardId, String questionType, String modelName);

    // 학습 계획 생성 (AI 호출 + 크레딧 소모). days는 3·5·7·14 중 하나이고 그 기간만큼만 짠다.
    // 다시 만들면 이전 계획을 덮어쓴다
    LearningCardResponseDTO createStudyPlan(Long userId, Long cardId, int days);

    // AI별 추천 스킬 목록 (LRN-005) — 큐레이션 데이터라 AI 호출도 크레딧도 없다
    // category는 약점 카테고리(BUG 등). null이면 provider만으로 거른다
    List<AiSkillResponseDTO> getAiSkills(Long userId, String provider, String category);

    // 내가 즐겨찾기한 스킬 전체 — provider 안 가리고 전부. AI 호출도 크레딧도 없다
    List<AiSkillResponseDTO> getFavoriteSkills(Long userId);

    // 스킬 즐겨찾기 토글 — 켜져 있으면 끄고, 꺼져 있으면 켠다
    void toggleSkillFavorite(Long userId, Long skillId);

    // AI 스킬 추천 (API-049) — 약점/요구사항을 넣으면 AI 호출 + 크레딧 소모로 추천 텍스트를 만든다
    SkillRecommendResponseDTO recommendSkill(Long userId, SkillRecommendRequestDTO request);

    // 약점 기반 AI별 스킬 추천 (신규) — 입력 없이 내 약점 통계로 도구별 추천 4건을 만든다. 고정 크레딧 2
    SkillByWeaknessResponseDTO recommendSkillByWeakness(Long userId);

    // 퀴즈 제출·채점 (API-046) — 정답이면 신호등 승급
    QuizSubmitResultDTO submitQuiz(Long userId, Long cardId, Long quizId, String answer);

    // 승급 히스토리 (API-047) — quiz_submissions에서 유도
    List<CardHistoryResponseDTO> getHistory(Long userId, Long cardId);

    // 지난 문제 보기 — 내가 푼 문제 + 내 답 + 정답 + 해설 (최근순)
    List<QuizSubmissionResponseDTO> getSubmissions(Long userId, Long cardId);
}
