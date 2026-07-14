package idu.sba.backend.domain.repo.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private Long invitedUserId; //nullable, 수락 시점에 설정

    @Enumerated(EnumType.STRING)
    private RepoInvitationStatus status;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime respondedAt; //nullable

    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
    }

    private RepoInvitation(Long repoId, Long inviterId, String invitedGithubUsername){
        this.repoId = repoId;
        this.inviterId = inviterId;
        this.invitedGithubUsername = invitedGithubUsername;
        this.status = RepoInvitationStatus.PENDING;
    }

    public static RepoInvitation issue(Long repoId, Long inviterId, String invitedGithubUsername){
        return new RepoInvitation(repoId, inviterId, invitedGithubUsername);
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
