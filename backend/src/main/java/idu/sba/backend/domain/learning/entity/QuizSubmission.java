package idu.sba.backend.domain.learning.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 퀴즈 제출 이력 한 건 (LRN-002). 등급 승급 근거이자, 후속 리텐션 streak의 기준이 된다.
@Entity
@Table(name = "quiz_submissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long quizId;

    private Long userId;

    private String submittedAnswer;

    private boolean isCorrect;

    private String gradeAfter; // 제출 직후 카드 등급

    @Column(updatable = false)
    private LocalDateTime submittedAt;

    @PrePersist
    protected void onCreate() {
        this.submittedAt = LocalDateTime.now();
    }

    private QuizSubmission(Long quizId, Long userId, String submittedAnswer, boolean isCorrect, String gradeAfter) {
        this.quizId = quizId;
        this.userId = userId;
        this.submittedAnswer = submittedAnswer;
        this.isCorrect = isCorrect;
        this.gradeAfter = gradeAfter;
    }

    public static QuizSubmission create(Long quizId, Long userId, String submittedAnswer, boolean isCorrect, String gradeAfter) {
        return new QuizSubmission(quizId, userId, submittedAnswer, isCorrect, gradeAfter);
    }
}
