package idu.sba.backend.domain.context.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class ContextExtractionRequestDTO {

    @NotBlank
    private String code; //분석할 원본 코드(전체 파일 또는 스니펫)

    private String fileName = "Unknown.java";

    private Integer startLine; //관심 범위 시작(선택, diff 대상 라인 등)
    private Integer endLine;   //관심 범위 끝(선택)

}
