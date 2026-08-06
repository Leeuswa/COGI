package idu.sba.backend.domain.learning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import idu.sba.backend.domain.learning.entity.LearningCardQuiz;
import idu.sba.backend.domain.learning.entity.QuizSubmission;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

// 지난 문제 한 건 (내가 푼 문제 + 내 답 + 정답 + 해설) — 학습 복습용
@Getter
public class QuizSubmissionResponseDTO {

    private final String questionType;
    private final String question;
    private final String submittedAnswer; // 내가 낸 답
    private final String answer;           // 정답

    @JsonProperty("isCorrect")
    private final boolean correct;

    private final String explain; // 해설 (정답이어도 보여줌)
    private final String submittedAt;

    private QuizSubmissionResponseDTO(QuizSubmission submission, LearningCardQuiz quiz) {
        this.questionType = quiz.getQuestionType();
        this.question = quiz.getQuestion();
        this.submittedAnswer = submission.getSubmittedAnswer();
        this.answer = quiz.getAnswer();
        this.correct = submission.isCorrect();
        this.explain = quiz.getExplain();
        this.submittedAt = submission.getSubmittedAt().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public static QuizSubmissionResponseDTO of(QuizSubmission submission, LearningCardQuiz quiz) {
        return new QuizSubmissionResponseDTO(submission, quiz);
    }
}
