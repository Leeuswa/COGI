package idu.sba.backend.domain.learning.service;

import idu.sba.backend.domain.learning.dto.CourseRecommendationResponseDTO;
import idu.sba.backend.domain.learning.dto.WeaknessStatResponseDTO;

import java.util.List;

public interface LearningService {

    // 약점 패턴 통계 조회 — 데이터 없으면 빈 목록(콜드스타트)
    List<WeaknessStatResponseDTO> getWeaknessStats(Long userId);

    // 약점별 강의 추천  — 약점 통계를 인프런/Udemy 검색 딥링크로 변환
    List<CourseRecommendationResponseDTO> getCourseRecommendations(Long userId);
}
