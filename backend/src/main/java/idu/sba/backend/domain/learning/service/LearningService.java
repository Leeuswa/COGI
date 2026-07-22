package idu.sba.backend.domain.learning.service;

import idu.sba.backend.domain.learning.dto.WeaknessStatResponseDTO;

import java.util.List;

public interface LearningService {

    // 약점 패턴 통계 조회 (API-042) — 데이터 없으면 빈 목록(콜드스타트)
    List<WeaknessStatResponseDTO> getWeaknessStats(Long userId);
}
