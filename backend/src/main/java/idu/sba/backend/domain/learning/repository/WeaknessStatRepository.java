package idu.sba.backend.domain.learning.repository;

import idu.sba.backend.domain.learning.entity.WeaknessStat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeaknessStatRepository extends JpaRepository<WeaknessStat, Long> {

    // 재집계 전에 기존 통계를 비운다 (조회 때마다 최신값으로 갈아끼우기)
    void deleteByUserId(Long userId);
}
