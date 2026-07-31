package idu.sba.backend.domain.review.dto;

import idu.sba.backend.domain.review.entity.ReviewQuestion;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ReviewQuestionResponseDTO {

    private final String question;
    private final String answer;
    private final LocalDateTime createdAt;

    private ReviewQuestionResponseDTO(String question, String answer, LocalDateTime createdAt) {
        this.question = question;
        this.answer = answer;
        this.createdAt = createdAt;
    }

    public static ReviewQuestionResponseDTO of(ReviewQuestion q) {
        return new ReviewQuestionResponseDTO(q.getQuestion(), q.getAnswer(), q.getCreatedAt());
    }
}
