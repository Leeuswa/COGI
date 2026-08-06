package idu.sba.backend.domain.review.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

//API-028: PR 리뷰에 사용할 AI 모델 선택
@Getter
@NoArgsConstructor
public class ModelSelectRequestDTO {

    @NotBlank(message = "선택할 모델을 입력해 주세요.")
    private String modelName;

}
