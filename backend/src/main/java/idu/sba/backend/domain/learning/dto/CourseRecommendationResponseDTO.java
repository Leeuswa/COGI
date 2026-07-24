package idu.sba.backend.domain.learning.dto;

import lombok.Getter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

// 강의 추천 한 줄  — 약점 하나를 인프런/Udemy 검색 딥링크로 변환
// 크롤링/저장 없이 (언어 + 카테고리)를 검색어로 만들어 각 사이트 검색결과로 보낸다
@Getter
public class CourseRecommendationResponseDTO {

    // 카테고리 코드 → 한글 라벨 (검색어·표시용). 매핑 없으면 코드 그대로 노출
    private static final Map<String, String> CATEGORY_KO = Map.of(
            "BUG", "버그", "PERFORMANCE", "성능", "CODE_SMELL", "코드 냄새",
            "CONVENTION", "컨벤션", "SECURITY", "보안");

    private final String category;        // 원본 코드 (BUG 등)
    private final String categoryLabel;   // 한글 라벨 (버그 등)
    private final String language;        // 언어, 미판별이면 null
    private final int occurrenceCount;    // 반복 횟수
    private final String query;           // 검색어 ("Java 보안")
    private final List<Link> links;       // 사이트별 딥링크

    @Getter
    public static class Link {
        private final String platform; // INFLEARN / UDEMY
        private final String label;    // "인프런에서 찾기"
        private final String url;

        private Link(String platform, String label, String url) {
            this.platform = platform;
            this.label = label;
            this.url = url;
        }
    }

    private CourseRecommendationResponseDTO(String category, String categoryLabel, String language,
                                            int occurrenceCount, String query, List<Link> links) {
        this.category = category;
        this.categoryLabel = categoryLabel;
        this.language = language;
        this.occurrenceCount = occurrenceCount;
        this.query = query;
        this.links = links;
    }

    public static CourseRecommendationResponseDTO of(WeaknessStatResponseDTO w) {
        String label = CATEGORY_KO.getOrDefault(w.getCategory(), w.getCategory());
        // 언어 판별됐으면 "Java 보안", 아니면 "보안"
        String query = (w.getLanguage() != null && !w.getLanguage().isBlank())
                ? w.getLanguage() + " " + label
                : label;
        String enc = URLEncoder.encode(query, StandardCharsets.UTF_8);

        List<Link> links = List.of(
                new Link("INFLEARN", "인프런에서 찾기",
                        // /ko/ 로 로케일 고정 — 없으면 접속 위치·브라우저 언어로 매번 다른 나라 페이지로 튕긴다
                        "https://www.inflearn.com/ko/courses?s=" + enc),
                new Link("UDEMY", "Udemy에서 찾기",
                        "https://www.udemy.com/courses/search/?q=" + enc + "&lang=ko"));

        return new CourseRecommendationResponseDTO(
                w.getCategory(), label, w.getLanguage(), w.getOccurrenceCount(), query, links);
    }
}
