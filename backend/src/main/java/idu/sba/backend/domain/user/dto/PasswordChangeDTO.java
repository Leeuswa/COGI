package idu.sba.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class PasswordChangeDTO {

    @NotBlank(message = "현재 비밀번호를 입력해 주세요")
    private String currentPassword;

    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
            message = "비밀번호는 영어·숫자·특수문자를 포함해 8자 이상이어야 합니다."
    )
    private String newPassword;

    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
            message = "새 비밀번호를 입력해 주세요."
    )
    private String newPasswordConfirm;

}
