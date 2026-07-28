package idu.sba.backend.domain.review.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

//이슈 재검증 [설계 추론] — 사용자가 고쳤다고 생각하는 코드를 다시 제출
@Getter
@NoArgsConstructor
public class ReverifyIssueRequestDTO {

    @NotBlank(message = "수정한 코드를 입력해 주세요.")
    private String fixedCode;

}
