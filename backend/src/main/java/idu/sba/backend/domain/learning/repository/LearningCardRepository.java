package idu.sba.backend.domain.learning.repository;

import idu.sba.backend.domain.learning.entity.LearningCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningCardRepository extends JpaRepository<LearningCard, Long> {

    // 보관함 목록 — 내 카드 최신순
    List<LearningCard> findByUserIdOrderByCreatedAtDesc(Long userId);
}
