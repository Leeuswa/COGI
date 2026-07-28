package idu.sba.backend.domain.learning.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 학습카드에서 생성된 퀴즈 한 문제 (LRN-002). 카드당 크레딧이 허용하는 한 반복 생성된다.
@Entity
@Table(name = "learning_card_quizzes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningCardQuiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long cardId;

    private String questionType; // MULTIPLE_CHOICE/OX/SHORT_ANSWER/FILL_BLANK

    private String modelName; // 이 문제를 만든 모델. 카드 모델과 다를 수 있어 따로 남긴다

    @Lob
    private String question;

    @Column(length = 1000)
    private String options; // 선택지 — 줄바꿈으로 이어 저장, 객관식/OX에만 있음(없으면 null)

    private String answer;


    // explain은 MariaDB 예약어라 그대로 쓰면 테이블 생성 DDL부터 깨진다. 컬럼명만 바꾸고 필드명은 유지
    @Lob
    @Column(name = "explanation")
    private String explain; // 해설 — 정답이든 오답이든 학습용으로 보여준다

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    private LearningCardQuiz(Long cardId, String questionType, String modelName,
                             String question, String options, String answer, String explain) {
        this.cardId = cardId;
        this.questionType = questionType;
        this.modelName = modelName;
        this.question = question;
        this.options = options;
        this.answer = answer;
        this.explain = explain;
    }

    public static LearningCardQuiz create(Long cardId, String questionType, String modelName,
                                          String question, String options, String answer, String explain) {
        return new LearningCardQuiz(cardId, questionType, modelName, question, options, answer, explain);
    }
}
