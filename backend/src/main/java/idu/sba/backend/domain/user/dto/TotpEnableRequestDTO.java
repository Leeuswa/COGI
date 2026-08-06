package idu.sba.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class TotpEnableRequestDTO {

    @NotBlank(message = "인증 코드를 입력해 주세요")
    private String code; //인증앱의 6자리
}
