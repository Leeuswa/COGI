package idu.sba.backend.domain.learning.repository;

import idu.sba.backend.domain.learning.entity.AiSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiSkillRepository extends JpaRepository<AiSkill, Long> {

    // 화면에서 고른 AI에 해당하는 스킬만
    List<AiSkill> findByProvider(String provider);

    // category까지 같이 고르면 그 안에서 한 번 더 거른다 (값은 IssueCategory 문자열과 동일)
    List<AiSkill> findByProviderAndCategory(String provider, String category);
}
