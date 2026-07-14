package idu.sba.backend.domain.repo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class RepoInviteRequestDTO {

    @NotBlank(message = "초대할 GitHub 아이디를 입력해 주세요.")
    private String githubUsername;

}
