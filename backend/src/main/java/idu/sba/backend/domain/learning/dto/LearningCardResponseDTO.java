package idu.sba.backend.domain.learning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import idu.sba.backend.domain.learning.entity.LearningCard;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

// 학습카드 응답 (목록/상세/생성 공용) — 프론트 Cards.tsx·CardDetail.tsx 필드명에 맞춤
@Getter
public class LearningCardResponseDTO {

    private final Long id;
    private final String category;
    private final String level;
    private final String language;
    private final String grade;
    private final int correctCount;

    @JsonProperty("isBookmarked") // JSON 키를 프론트가 읽는 isBookmarked 로 고정
    private final boolean bookmarked;

    @JsonProperty("isCompleted")
    private final boolean completed;

    private final String conceptContent;
    private final String whyItMatters;
    private final String badExample;
    private final String goodExample;
    private final String diffExplain;
    private final List<String> keyPoints;
    private final List<String> pitfalls;
    private final List<String> selfCheck;

    // 저장해 둔 계획 JSON을 그대로 내보낸다. 저장 전에 파싱해 검증했으므로 깨진 JSON이 나갈 일은 없다
    @JsonRawValue
    private final String studyPlan;

    // 계획을 달력에 등록한 날. null이면 아직 등록 전 — 화면에서 버튼 문구를 가른다
    private final LocalDate planStartedAt;

    private LearningCardResponseDTO(LearningCard card) {
        this.id = card.getId();
        this.category = card.getCategory();
        this.level = card.getLevel();
        this.language = card.getLanguage();
        this.grade = card.getGrade();
        this.correctCount = card.getCorrectCount();
        this.bookmarked = card.isBookmarked();
        this.completed = card.isCompleted();
        this.conceptContent = card.getConceptContent();
        this.whyItMatters = card.getWhyItMatters();
        this.badExample = card.getBadExample();
        this.goodExample = card.getGoodExample();
        this.diffExplain = card.getDiffExplain();
        this.keyPoints = splitLines(card.getKeyPoints());
        this.pitfalls = splitLines(card.getPitfalls());
        this.selfCheck = splitLines(card.getSelfCheck());
        this.studyPlan = card.getStudyPlan();
        this.planStartedAt = card.getPlanStartedAt();
    }

    public static LearningCardResponseDTO of(LearningCard card) {
        return new LearningCardResponseDTO(card);
    }

    // 줄바꿈으로 이어 저장한 목록을 배열로 되돌린다. 예전에 만든 카드는 비어 있어 빈 배열이 나간다
    private static List<String> splitLines(String value) {
        return (value == null || value.isBlank()) ? List.of() : List.of(value.split("\n"));
    }
}
