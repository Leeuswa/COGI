package idu.sba.backend.domain.repo.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "repo_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepoInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long repoId;
    private Long inviterId;
    private String invitedGithubUsername;
    private Long invitedUserId; //nullable — GitHub 연동된 기존 사용자면 초대 즉시 설정, 이메일 경로면 가입/연동 완료 시 설정

    private String inviteeEmail; //nullable — githubUsername으로 대상을 못 찾았을 때만 사용
    @Column(unique = true)
    private String inviteToken; //nullable — 이메일 경로일 때만 발급(추측 불가능한 값)
    private LocalDateTime expiresAt; //nullable — 이메일 경로일 때만 사용

    @Enumerated(EnumType.STRING)
    private RepoInvitationStatus status;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime respondedAt; //nullable

    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
    }

    private RepoInvitation(Long repoId, Long inviterId, String invitedGithubUsername, Long invitedUserId,
                            String inviteeEmail, String inviteToken, LocalDateTime expiresAt){
        this.repoId = repoId;
        this.inviterId = inviterId;
        this.invitedGithubUsername = invitedGithubUsername;
        this.invitedUserId = invitedUserId;
        this.inviteeEmail = inviteeEmail;
        this.inviteToken = inviteToken;
        this.expiresAt = expiresAt;
        this.status = RepoInvitationStatus.PENDING;
    }

    //① 가입 + GitHub 연동된 사용자를 즉시 찾은 경우
    public static RepoInvitation issueMatched(Long repoId, Long inviterId, String invitedGithubUsername, Long matchedUserId){
        return new RepoInvitation(repoId, inviterId, invitedGithubUsername, matchedUserId, null, null, null);
    }

    //② 미가입 또는 GitHub 미연동 — 이메일 경로
    public static RepoInvitation issueByEmail(Long repoId, Long inviterId, String invitedGithubUsername, String inviteeEmail, int ttlHours){
        return new RepoInvitation(repoId, inviterId, invitedGithubUsername, null,
                inviteeEmail, UUID.randomUUID().toString(), LocalDateTime.now().plusHours(ttlHours));
    }

    public boolean isEmailInvite(){
        return inviteeEmail != null;
    }

    public boolean isExpired(){
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public void accept(Long userId){
        this.status = RepoInvitationStatus.ACCEPTED;
        this.invitedUserId = userId;
        this.respondedAt = LocalDateTime.now();
    }

    public void reject(){
        this.status = RepoInvitationStatus.REJECTED;
        this.respondedAt = LocalDateTime.now();
    }

}
