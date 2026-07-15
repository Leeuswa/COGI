package idu.sba.backend.domain.repo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class RepoInviteRequestDTO {

    @NotBlank(message = "초대할 GitHub 아이디를 입력해 주세요.")
    private String githubUsername;

    //githubUsername으로 대상을 못 찾았을 때만 사용(1차 호출에선 비워서 요청, GITHUB_USER_NOT_FOUND 응답 후 채워서 재요청)
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

}
