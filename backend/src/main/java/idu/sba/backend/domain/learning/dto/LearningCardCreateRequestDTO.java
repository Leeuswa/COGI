package idu.sba.backend.domain.learning.dto;

import lombok.Getter;

// 학습카드 생성 요청 (API-043) — 프론트가 { category, level, language }를 보낸다
@Getter
public class LearningCardCreateRequestDTO {
    private String category;
    private String level;    // BEGINNER/INTERMEDIATE/ADVANCED
    private String language; // nullable
}
