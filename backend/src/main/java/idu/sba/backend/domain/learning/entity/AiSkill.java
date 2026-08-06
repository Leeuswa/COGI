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

    private Long userId; // null이면 관리자 큐레이션 공용 스킬, 있으면 그 사용자에게 AI가 만들어 준 스킬

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

    @Lob
    @Column(length = 65535)
    private String prompt; // AI가 만든 스킬의 붙여넣기용 프롬프트 — 공용 큐레이션 행은 null

    private AiSkill(Long userId, String provider, String category, String title, String description, String howTo, String url, String prompt) {
        this.userId = userId;
        this.provider = provider;
        this.category = category;
        this.title = title;
        this.description = description;
        this.howTo = howTo;
        this.url = url;
        this.prompt = prompt;
    }

    public static AiSkill of(String provider, String category, String title, String description, String howTo, String url) {
        return new AiSkill(null, provider, category, title, description, howTo, url, null);
    }

    // 약점 기반 추천이 만들어 준 스킬 — 사용자 소유라 userId가 있고 url 대신 prompt를 들고 있다
    public static AiSkill ofGenerated(Long userId, String provider, String category, String title, String description, String howTo, String prompt) {
        return new AiSkill(userId, provider, category, title, description, howTo, null, prompt);
    }

    // 같은 사용자+provider+title로 다시 추천이 오면 새로 안 만들고 여기로 내용만 갱신한다
    public void updateGenerated(String category, String description, String howTo, String prompt) {
        this.category = category;
        this.description = description;
        this.howTo = howTo;
        this.prompt = prompt;
    }
}
