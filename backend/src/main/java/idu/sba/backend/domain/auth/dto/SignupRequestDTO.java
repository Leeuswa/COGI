package idu.sba.backend.domain.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;

import java.util.List;

@Getter
public class SignupRequestDTO {

    @NotBlank
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @NotBlank
    //영어,숫자,특수문자 포함 8자리
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
            message = "비밀번호는 영문·숫자·특수문자를 포함해 8자 이상이어야 합니다."
    )
    private String password;
    @NotBlank
    private String passwordConfirm; //비밀번호 확인


    private String nickname;

    @NotEmpty(message = "약관 동의가 필요합니다.")
    private List<Long> termIds; //동의한 약관 ID 목록

}
