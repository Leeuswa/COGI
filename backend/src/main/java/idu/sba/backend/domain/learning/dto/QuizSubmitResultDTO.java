package idu.sba.backend.domain.learning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

// 퀴즈 제출 결과 (API-046) — 프론트는 isCorrect를 쓰고, grade/correctCount는 참고용으로 함께 내려준다
@Getter
public class QuizSubmitResultDTO {

    @JsonProperty("isCorrect") // JSON 키를 프론트가 읽는 isCorrect 로 고정
    private final boolean correct;

    private final String grade;
    private final int correctCount;

    private QuizSubmitResultDTO(boolean correct, String grade, int correctCount) {
        this.correct = correct;
        this.grade = grade;
        this.correctCount = correctCount;
    }

    public static QuizSubmitResultDTO of(boolean correct, String grade, int correctCount) {
        return new QuizSubmitResultDTO(correct, grade, correctCount);
    }
}
