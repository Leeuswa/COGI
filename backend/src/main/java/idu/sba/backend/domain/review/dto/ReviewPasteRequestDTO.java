package idu.sba.backend.domain.review.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReviewPasteRequestDTO {

    @NotBlank(message = "리뷰할 코드를 입력해 주세요.")
    private String code;

    private String language; //nullable
    private String modelName; //nullable — 미지정 시 플랜 기본 모델

}
