package idu.sba.backend.domain.learning.repository;

import idu.sba.backend.domain.learning.entity.LearningCardQuiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningCardQuizRepository extends JpaRepository<LearningCardQuiz, Long> {

    // 히스토리 유도용 — 카드에 속한 퀴즈 id를 모은다
    List<LearningCardQuiz> findByCardId(Long cardId);
}
