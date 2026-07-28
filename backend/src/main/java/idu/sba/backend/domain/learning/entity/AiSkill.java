package idu.sba.backend.domain.learning.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// AI별 추천 스킬 한 줄 (LRN-005). 사용자가 쓰는 AI를 고르면 그 AI에 맞는 스킬만 보여준다.
// 관리자가 큐레이션해 넣는 데이터라 AI 호출도 크레딧 소모도 없다.
@Entity
@Table(name = "ai_skills")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String provider; // CLAUDE / CHATGPT / GEMINI / COPILOT

    private String category; // 어떤 약점에 듣는지 — review_issues의 카테고리와 같은 값

    private String title;

    @Lob
    @Column(length = 65535)
    private String description; // 이 스킬이 뭘 해주는지

    @Lob
    @Column(length = 65535)
    private String howTo; // 실제로 어떻게 쓰는지 (프롬프트 예시나 설정 방법)

    private String url; // 공식 문서 링크, 없으면 null

    private AiSkill(String provider, String category, String title, String description, String howTo, String url) {
        this.provider = provider;
        this.category = category;
        this.title = title;
        this.description = description;
        this.howTo = howTo;
        this.url = url;
    }

    public static AiSkill of(String provider, String category, String title, String description, String howTo, String url) {
        return new AiSkill(provider, category, title, description, howTo, url);
    }
}
