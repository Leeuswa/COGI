package idu.sba.backend.domain.learning.repository;

import idu.sba.backend.domain.learning.entity.AiSkillRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

// 호출마다 한 행 저장하는 이력용 — 지금은 조회 엔드포인트가 없어 save만 쓴다
public interface AiSkillRecommendationRepository extends JpaRepository<AiSkillRecommendation, Long> {
}
