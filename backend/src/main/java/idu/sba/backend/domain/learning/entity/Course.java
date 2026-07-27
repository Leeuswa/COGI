package idu.sba.backend.domain.learning.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 큐레이션 강의 마스터 (LRN-003~004) — 약점 category로 매칭해 추천한다.
// 데이터는 seed-courses.sql로 채우는 전역 마스터(사용자별 아님). 조회 전용.
@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String category; // 약점 카테고리 코드 (BUG/PERFORMANCE/CODE_SMELL/CONVENTION/SECURITY)
    private String language; // 대상 언어 (Java 등). null = 언어무관(모든 언어 약점에 노출)
    private String platform; // INFLEARN / UDEMY
    private String title;
    private String description;
    private String url;

    private Course(String category, String language, String platform, String title, String description, String url) {
        this.category = category;
        this.language = language;
        this.platform = platform;
        this.title = title;
        this.description = description;
        this.url = url;
    }

    public static Course of(String category, String language, String platform, String title, String description, String url) {
        return new Course(category, language, platform, title, description, url);
    }
}
