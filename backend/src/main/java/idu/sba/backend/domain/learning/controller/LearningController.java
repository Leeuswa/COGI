package idu.sba.backend.domain.learning.controller;

import idu.sba.backend.domain.learning.dto.CourseRecommendationResponseDTO;
import idu.sba.backend.domain.learning.dto.WeaknessStatResponseDTO;
import idu.sba.backend.domain.learning.service.LearningService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class LearningController {

    private final LearningService learningService;

    //내 약점 패턴 통계 조회 (3회 이상, 언어 구분). 데이터 없으면 빈 목록
    @GetMapping("/weakness-stats")
    public ApiResponse<List<WeaknessStatResponseDTO>> getWeaknessStats(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(learningService.getWeaknessStats(userId));
    }

    //약점별 강의 추천 (인프런/Udemy 검색 딥링크). 약점 없으면 빈 목록
    @GetMapping("/course-recommendations")
    public ApiResponse<List<CourseRecommendationResponseDTO>> getCourseRecommendations(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(learningService.getCourseRecommendations(userId));
    }
}
