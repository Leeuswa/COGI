package idu.sba.backend.domain.review.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IssueFinalizeRequestDTO {

    @NotBlank(message = "판정 결과를 선택해 주세요.")
    private String verdict; // RESOLVED 또는 IGNORED — 그 외 값은 서비스에서 INVALID_INPUT

}
