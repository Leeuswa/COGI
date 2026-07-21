package idu.sba.backend.domain.repo.dto;

import lombok.Getter;

@Getter
public class RepoInvitationLookupResponseDTO {

    private final String repoName;
    private final boolean emailHasAccount; //true면 로그인 후 GitHub 연동으로, false면 GitHub 신규가입으로 안내

    private RepoInvitationLookupResponseDTO(String repoName, boolean emailHasAccount) {
        this.repoName = repoName;
        this.emailHasAccount = emailHasAccount;
    }

    public static RepoInvitationLookupResponseDTO of(String repoName, boolean emailHasAccount) {
        return new RepoInvitationLookupResponseDTO(repoName, emailHasAccount);
    }

}
