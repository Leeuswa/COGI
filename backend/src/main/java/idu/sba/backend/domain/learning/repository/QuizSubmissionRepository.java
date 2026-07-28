package idu.sba.backend.domain.learning.repository;

import idu.sba.backend.domain.learning.entity.QuizSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {

    // 특정 카드의 내 제출 이력(시간순) — 카드 퀴즈 id 목록으로 조회, 히스토리 유도용
    List<QuizSubmission> findByUserIdAndQuizIdInOrderBySubmittedAtAsc(Long userId, List<Long> quizIds);

    // 지난 문제 보기 — 최근에 푼 것부터
    List<QuizSubmission> findByUserIdAndQuizIdInOrderBySubmittedAtDesc(Long userId, List<Long> quizIds);

    // 주간 리포트용 — 기간 [from, to) 내 제출 수 / 정답 수
    long countByUserIdAndSubmittedAtGreaterThanEqualAndSubmittedAtLessThan(Long userId, LocalDateTime from, LocalDateTime to);
    long countByUserIdAndIsCorrectTrueAndSubmittedAtGreaterThanEqualAndSubmittedAtLessThan(Long userId, LocalDateTime from, LocalDateTime to);

    // 제출 달력 점 — 최근 몇 주치만 가져와 서비스에서 날짜로 접는다.
    // DB에서 distinct date로 뽑으면 벤더 함수를 타야 해서, 몇 주 분량은 그냥 다 읽는 게 싸다
    List<QuizSubmission> findByUserIdAndSubmittedAtGreaterThanEqual(Long userId, LocalDateTime from);
}
