package idu.sba.backend.domain.repo.dto;

import idu.sba.backend.domain.repo.entity.RepoInvitation;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class RepoInvitationResponseDTO {

    private final Long id;
    private final String invitedGithubUsername;
    private final String status;
    private final LocalDateTime createdAt;

    private RepoInvitationResponseDTO(Long id, String invitedGithubUsername, String status, LocalDateTime createdAt){
        this.id = id;
        this.invitedGithubUsername = invitedGithubUsername;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static RepoInvitationResponseDTO of(RepoInvitation invitation){
        return new RepoInvitationResponseDTO(
                invitation.getId(), invitation.getInvitedGithubUsername(),
                invitation.getStatus().name(), invitation.getCreatedAt());
    }

}
