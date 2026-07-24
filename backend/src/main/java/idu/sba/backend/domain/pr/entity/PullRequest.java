package idu.sba.backend.domain.pr.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 테이블 정의서의 pull_requests — Webhook으로 감지된 PR 정보(재처리 정책 반영, 선택 AI 모델 포함).
 * 관련: API-024(웹훅 수신), API-025(리뷰 결과 조회), API-028(모델 선택), API-032(팀 PR 대시보드, 미구현)
 */
@Entity
@Table(name = "pull_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PullRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long repoId; //FK repositories.id
    private Integer githubPrNumber;
    private String title; //nullable
    private Long authorId; //nullable — githubUsername으로 매칭 실패 시(미가입자) null
    private String authorLogin; //nullable — GitHub 로그인명 원본. authorId가 안 채워졌을 때 표시용 폴백
    private String selectedModel; //nullable — null이면 플랜 기본 모델(API-028)

    @Enumerated(EnumType.STRING)
    private PullRequestStatus status;

    private Integer webhookRetryCount;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate(){
        this.updatedAt = LocalDateTime.now();
    }

    private PullRequest(Long repoId, Integer githubPrNumber, String title, Long authorId, String authorLogin){
        this.repoId = repoId;
        this.githubPrNumber = githubPrNumber;
        this.title = title;
        this.authorId = authorId;
        this.authorLogin = authorLogin;
        this.status = PullRequestStatus.OPEN;
        this.webhookRetryCount = 0;
    }

    public static PullRequest open(Long repoId, Integer githubPrNumber, String title, Long authorId, String authorLogin){
        return new PullRequest(repoId, githubPrNumber, title, authorId, authorLogin);
    }

    //synchronize/reopened 이벤트 — 기존 row를 재사용해 다시 리뷰 대상으로 되돌림
    public void reopenForReview(String title, Long authorId, String authorLogin){
        this.title = title;
        this.authorId = authorId;
        this.authorLogin = authorLogin;
        this.status = PullRequestStatus.OPEN;
    }

    public void markReviewed(){
        this.status = PullRequestStatus.REVIEWED;
    }

    //API-028 — 다음 리뷰(웹훅 재실행)부터 이 모델이 적용됨. 이미 끝난 리뷰를 소급 변경하지는 않음
    public void selectModel(String modelName){
        this.selectedModel = modelName;
    }

    //재시도 스케줄러는 이번 범위 밖 — webhookRetryCount는 증가만 시켜 다음 작업에서 쓸 수 있게 남겨둔다
    public void markFailed(){
        this.status = PullRequestStatus.FAILED;
        this.webhookRetryCount++;
    }

}
