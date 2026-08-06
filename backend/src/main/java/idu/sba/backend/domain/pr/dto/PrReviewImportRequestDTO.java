package idu.sba.backend.domain.pr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Studio "PR 가져오기"로 선택한 파일을 리뷰 요청할 때 쓰는 바디 [설계 추론]
@Getter
@NoArgsConstructor
public class PrReviewImportRequestDTO {

    @NotBlank(message = "리뷰할 코드를 입력해 주세요.")
    private String code; //프론트가 선택한 파일들을 이어붙인 diff 텍스트

    private String modelName; //nullable — 미지정 시 플랜 기본 모델
    private String title; //nullable — PullRequest row 생성/갱신에 씀(피커에서 이미 조회한 값)
    private String authorLogin; //nullable — GitHub 로그인명. COGI 가입자면 authorId로 매칭

}
