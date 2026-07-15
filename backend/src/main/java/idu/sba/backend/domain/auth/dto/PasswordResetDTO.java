package idu.sba.backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class PasswordResetDTO {

    @NotBlank
    private String resetToken;

    //새 비밀번호 (영어,숫자,특수문자)
    @NotBlank
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
            message = "비밀번호는 영문·숫자·특수문자를 포함해 8자 이상이어야 합니다."
    )
    private String newPassword;

    @NotBlank
    private String newPasswordConfirm; // 새 비밀번호 확인

}
