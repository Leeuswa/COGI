package idu.sba.backend.domain.review.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 리뷰 후속 질문·답변 기록 — 예전엔 프론트 sessionStorage에만 담겨서 탭을 닫거나 새 리뷰를
// 시작하면 영구히 사라졌고, 리뷰 히스토리/PR 리뷰 상세를 다시 봐도 흔적이 없었음(2026-07-29 발견).
@Entity
@Table(name = "review_questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reviewId;

    @Lob
    private String question;

    @Lob
    private String answer;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
    }

    private ReviewQuestion(Long reviewId, String question, String answer){
        this.reviewId = reviewId;
        this.question = question;
        this.answer = answer;
    }

    public static ReviewQuestion of(Long reviewId, String question, String answer){
        return new ReviewQuestion(reviewId, question, answer);
    }

}
