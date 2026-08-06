package idu.sba.backend.domain.learning.repository;

import idu.sba.backend.domain.learning.entity.AiSkillRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// 호출마다 한 행 저장하는 이력용
public interface AiSkillRecommendationRepository extends JpaRepository<AiSkillRecommendation, Long> {

    // 마지막 약점 기반 추천 다시 꺼내기 — 자유 입력(FREE_TEXT) 이력은 섞이면 안 된다
    Optional<AiSkillRecommendation> findTopByUserIdAndKindOrderByCreatedAtDesc(Long userId, String kind);
}
