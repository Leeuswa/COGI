package idu.sba.backend.domain.review.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

//리뷰 후속 질문 [설계 추론]
@Getter
@NoArgsConstructor
public class AskQuestionRequestDTO {

    @NotBlank(message = "질문 내용을 입력해 주세요.")
    private String question;

}
