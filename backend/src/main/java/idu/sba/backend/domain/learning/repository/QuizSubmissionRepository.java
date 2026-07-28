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
}
