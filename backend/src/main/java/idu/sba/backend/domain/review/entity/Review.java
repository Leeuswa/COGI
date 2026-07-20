package idu.sba.backend.domain.review.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ReviewTargetType targetType;

    private Long prId; //nullable — PR 웹훅 경로 전용, 이번 범위에서는 항상 null
    private Long userId; //nullable — 게스트 리뷰면 null(이 경로는 항상 로그인 사용자라 항상 채워짐)

    private String modelName;
    private String language; //nullable

    @Enumerated(EnumType.STRING)
    private ReviewStatus status;

    @Lob
    private String sourceCode; //PASTE/UPLOAD 전용

    private String originalFilename; //nullable — UPLOAD만
    private String guestToken; //nullable — 게스트 리뷰 전용, 이 경로는 항상 null

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
    }

    private Review(ReviewTargetType targetType, Long userId, String modelName, String language,
                    String sourceCode, String originalFilename){
        this.targetType = targetType;
        this.userId = userId;
        this.modelName = modelName;
        this.language = language;
        this.sourceCode = sourceCode;
        this.originalFilename = originalFilename;
        this.status = ReviewStatus.PENDING;
    }

    public static Review createPaste(Long userId, String code, String language, String modelName){
        return new Review(ReviewTargetType.PASTE, userId, modelName, language, code, null);
    }

    public static Review createUpload(Long userId, String code, String language, String originalFilename, String modelName){
        return new Review(ReviewTargetType.UPLOAD, userId, modelName, language, code, originalFilename);
    }

    // 비로그인 게스트가 회원가입 후 자기 리뷰를 계정으로 가져올 때(claim).
    // 이미 리뷰가 끝난 결과를 옮기는 것이므로 GUEST 타입 + COMPLETED 로 저장한다.
    public static Review createFromGuest(Long userId, String code, String language, String modelName, String guestToken){
        Review review = new Review(ReviewTargetType.GUEST, userId, modelName, language, code, null);
        review.guestToken = guestToken;
        review.markCompleted();
        return review;
    }

    public void markCompleted(){
        this.status = ReviewStatus.COMPLETED;
    }

    public void markFailed(){
        this.status = ReviewStatus.FAILED;
    }

}
