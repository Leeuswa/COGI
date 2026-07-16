package idu.sba.backend.domain.repo.dto;

import idu.sba.backend.domain.repo.entity.RepoInvitation;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class RepoInvitationResponseDTO {

    private final Long id;
    private final String invitedGithubUsername;
    private final String status;
    private final boolean viaEmail; //true면 이메일 경로로 발송됨(가입/GitHub연동 완료 시 자동 매칭 대기)
    private final LocalDateTime createdAt;

    private RepoInvitationResponseDTO(Long id, String invitedGithubUsername, String status, boolean viaEmail, LocalDateTime createdAt){
        this.id = id;
        this.invitedGithubUsername = invitedGithubUsername;
        this.status = status;
        this.viaEmail = viaEmail;
        this.createdAt = createdAt;
    }

    public static RepoInvitationResponseDTO of(RepoInvitation invitation){
        return new RepoInvitationResponseDTO(
                invitation.getId(), invitation.getInvitedGithubUsername(),
                invitation.getStatus().name(), invitation.isEmailInvite(), invitation.getCreatedAt());
    }

}
