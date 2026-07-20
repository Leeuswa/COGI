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
    private String fullName; //nullable — "owner/repo" 형태, PR 파일/diff 조회(API-024/025)에 필요.
                              //마이그레이션 이전에 연동된 레포는 null이었다가 첫 웹훅 수신 시 self-heal로 채워짐

    @Column(updatable = false)
    private LocalDateTime linkedAt;

    @PrePersist
    protected void onCreate(){
        this.linkedAt = LocalDateTime.now();
    }

    private GithubRepository(Long userId, String githubRepoId, String repoName, boolean isPrivate, String fullName){
        this.userId = userId;
        this.githubRepoId = githubRepoId;
        this.repoName = repoName;
        this.isPrivate = isPrivate;
        this.fullName = fullName;
    }

    public static GithubRepository link(Long userId, String githubRepoId, String repoName, boolean isPrivate, String fullName){
        return new GithubRepository(userId, githubRepoId, repoName, isPrivate, fullName);
    }

    //팀장 위임 시 함께 호출 — RepoMember.role만 바뀌고 이 필드가 그대로면 "연동 등록자"가 위임 후에도
    //예전 팀장을 계속 가리키는 정합성 문제가 생긴다. 트랜잭션 안에서 호출되면 dirty checking으로 자동 반영됨.
    public void changeOwner(Long newOwnerId){
        this.userId = newOwnerId;
    }

    //마이그레이션 이전에 연동된 레포는 fullName이 null — 웹훅 payload의 repository.full_name으로 self-heal
    public void assignFullName(String fullName){
        this.fullName = fullName;
    }

}
