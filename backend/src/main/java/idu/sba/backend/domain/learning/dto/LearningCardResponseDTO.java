package idu.sba.backend.domain.learning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import idu.sba.backend.domain.learning.entity.LearningCard;
import lombok.Getter;

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
    private final String badExample;
    private final String goodExample;
    private final List<String> keyPoints;

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
        this.badExample = card.getBadExample();
        this.goodExample = card.getGoodExample();
        // 줄바꿈으로 저장된 핵심 포인트를 배열로 분리 (없으면 빈 배열)
        this.keyPoints = (card.getKeyPoints() == null || card.getKeyPoints().isBlank())
                ? List.of()
                : List.of(card.getKeyPoints().split("\n"));
    }

    public static LearningCardResponseDTO of(LearningCard card) {
        return new LearningCardResponseDTO(card);
    }
}
