package idu.sba.backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class TotpVerifyRequestDTO {

    @NotBlank
    private String tempToken; //로그인 1단계에서 받은 임시토큰

    @NotBlank
    private String totpCode; //인증앱 6자리
}
