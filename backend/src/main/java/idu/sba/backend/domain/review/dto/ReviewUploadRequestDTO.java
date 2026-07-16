package idu.sba.backend.domain.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// multipart 요청(API-041)의 파일 내용을 컨트롤러가 미리 문자열로 읽어 채워 넣는 DTO
@Getter
@AllArgsConstructor
public class ReviewUploadRequestDTO {

    private final String code;
    private final String language; //nullable
    private final String originalFilename;
    private final String modelName; //nullable — 미지정 시 플랜 기본 모델

}
