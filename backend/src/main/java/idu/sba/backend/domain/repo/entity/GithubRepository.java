package idu.sba.backend.domain.repo.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 테이블 정의서의 repositories — GitHub 레포지토리 연동 정보.
 * Webhook 자동 등록(webhookId)은 별도 작업(Webhook 수신)의 몫이라 이번 연동 단계에서는 null로 남긴다.
 */
@Entity
@Table(name = "repositories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId; //연동 등록자(팀장)
    private String githubRepoId;
    private String repoName;
    private Boolean isPrivate = false;
    private String webhookId; //nullable — Webhook 수신 작업에서 채워짐

    @Column(updatable = false)
    private LocalDateTime linkedAt;

    @PrePersist
    protected void onCreate(){
        this.linkedAt = LocalDateTime.now();
    }

    private GithubRepository(Long userId, String githubRepoId, String repoName, boolean isPrivate){
        this.userId = userId;
        this.githubRepoId = githubRepoId;
        this.repoName = repoName;
        this.isPrivate = isPrivate;
    }

    public static GithubRepository link(Long userId, String githubRepoId, String repoName, boolean isPrivate){
        return new GithubRepository(userId, githubRepoId, repoName, isPrivate);
    }

}
