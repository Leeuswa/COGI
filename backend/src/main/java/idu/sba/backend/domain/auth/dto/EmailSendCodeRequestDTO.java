package idu.sba.backend.domain.auth.dto;

import idu.sba.backend.domain.auth.entity.Purpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

//이메일 코드 발송

@Getter
public class EmailSendCodeRequestDTO {

    @NotBlank
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email; //인증 받을 이메일

    @NotNull
    private Purpose purpose;

}
