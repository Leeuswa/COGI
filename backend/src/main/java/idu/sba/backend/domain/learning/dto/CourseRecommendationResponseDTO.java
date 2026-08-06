package idu.sba.backend.domain.learning.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CourseRecommendationResponseDTO {

    // 강의 제목
    private String title;

    // 검색 결과 설명
    private String description;

    // 강의 URL
    private String url;

    // 플랫폼 (INFLEARN, UDEMY)
    private String platform;
}