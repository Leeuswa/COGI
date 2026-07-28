package idu.sba.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class WithdrawRequestDTO {

    // 소셜·로컬 통일: 비밀번호 대신 정해진 문구를 정확히 입력해야 탈퇴 진행(서버에서 재검증)
    @NotBlank(message = "탈퇴 확인 문구를 입력해 주세요.")
    private String confirm;

}
