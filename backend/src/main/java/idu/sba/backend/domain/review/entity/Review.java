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

    public void markCompleted(){
        this.status = ReviewStatus.COMPLETED;
    }

    public void markFailed(){
        this.status = ReviewStatus.FAILED;
    }

}
