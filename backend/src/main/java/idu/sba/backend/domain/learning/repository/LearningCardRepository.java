package idu.sba.backend.domain.learning.repository;

import idu.sba.backend.domain.learning.entity.LearningCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningCardRepository extends JpaRepository<LearningCard, Long> {

    // 보관함 목록 — 내 카드 최신순
    List<LearningCard> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 달력·알림이 쓰는 목록 — 계획을 등록해 둔 카드만. 등록 안 한 카드는 날짜가 없어 볼 게 없다
    List<LearningCard> findByUserIdAndPlanStartedAtIsNotNull(Long userId);
}
