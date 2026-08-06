package idu.sba.backend.domain.pr.dto;

import idu.sba.backend.domain.repo.client.GithubPrFileDto;
import lombok.Getter;

// Studio "PR 가져오기" 피커의 파일 목록 항목. code는 GitHub의 patch(unified diff)를 그대로 씀 —
// 전체 파일 내용을 받으려면 파일별로 GitHub contents API를 또 불러야 해서 이번 범위에서는
// diff를 붙여넣기 리뷰 입력으로 쓰는 근사치로 처리(알려진 한계).
@Getter
public class PrFileResponseDTO {

    private final String path;
    private final String code;

    private PrFileResponseDTO(String path, String code) {
        this.path = path;
        this.code = code;
    }

    public static PrFileResponseDTO of(GithubPrFileDto dto) {
        return new PrFileResponseDTO(dto.getFilename(), dto.getPatch());
    }

}
