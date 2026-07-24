package idu.sba.backend.domain.learning.dto;

import idu.sba.backend.domain.learning.entity.LearningCardQuiz;
import lombok.Getter;

import java.util.List;

// 퀴즈 응답 (API-045) — 프론트 CardDetail.tsx가 읽는 필드에 맞춤
@Getter
public class QuizResponseDTO {

    private final Long id;
    private final Long cardId;
    private final String questionType;
    private final String question;
    private final List<String> options; // 객관식/OX면 보기 배열, 아니면 null(텍스트 입력)
    private final String answer;         // 오답 시 정답 노출용 — 프론트가 이미 이 필드를 쓴다
    private final String explain;        // 해설 — 정답이어도 보여준다

    private QuizResponseDTO(LearningCardQuiz quiz) {
        this.id = quiz.getId();
        this.cardId = quiz.getCardId();
        this.questionType = quiz.getQuestionType();
        this.question = quiz.getQuestion();
        this.options = (quiz.getOptions() == null || quiz.getOptions().isBlank())
                ? null
                : List.of(quiz.getOptions().split("\n"));
        this.answer = quiz.getAnswer();
        this.explain = quiz.getExplain();
    }

    public static QuizResponseDTO of(LearningCardQuiz quiz) {
        return new QuizResponseDTO(quiz);
    }
}
