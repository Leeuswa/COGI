package idu.sba.backend.domain.learning.repository;

import idu.sba.backend.domain.learning.entity.AiSkillFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiSkillFavoriteRepository extends JpaRepository<AiSkillFavorite, Long> {

    // 목록에 별을 칠하려면 내가 찜한 것만 알면 된다
    List<AiSkillFavorite> findByUserId(Long userId);

    // 토글 해제용
    void deleteByUserIdAndSkillId(Long userId, Long skillId);

    boolean existsByUserIdAndSkillId(Long userId, Long skillId);
}
