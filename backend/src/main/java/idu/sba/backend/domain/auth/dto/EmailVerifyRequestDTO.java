package idu.sba.backend.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class EmailVerifyRequestDTO {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String code; //사용자가 입력한 6자리 인증코드

}
