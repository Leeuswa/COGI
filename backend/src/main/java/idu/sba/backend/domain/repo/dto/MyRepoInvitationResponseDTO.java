package idu.sba.backend.domain.repo.dto;

import idu.sba.backend.domain.repo.entity.RepoInvitation;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MyRepoInvitationResponseDTO {

    private final Long invitationId;
    private final Long repoId;
    private final String repoName;
    private final LocalDateTime createdAt;

    private MyRepoInvitationResponseDTO(Long invitationId, Long repoId, String repoName, LocalDateTime createdAt){
        this.invitationId = invitationId;
        this.repoId = repoId;
        this.repoName = repoName;
        this.createdAt = createdAt;
    }

    public static MyRepoInvitationResponseDTO of(RepoInvitation invitation, String repoName){
        return new MyRepoInvitationResponseDTO(
                invitation.getId(), invitation.getRepoId(), repoName, invitation.getCreatedAt());
    }

}
