package idu.sba.backend.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class PasswordResetRequestDTO {

    @NotBlank
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @NotBlank
    private String code; //이메일로 인증받은 코드

}
