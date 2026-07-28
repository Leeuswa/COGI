package idu.sba.backend.domain.repo.dto;

import lombok.Getter;

//미리보기 부족 파일 채우기 응답 — encoding으로 프론트가 텍스트(style로 인라인)/바이너리(data URI) 를 구분
@Getter
public class RepoFileResponseDTO {

    private final String path;
    private final String content;
    private final long size;
    private final String encoding; // "utf-8" 또는 "base64"

    private RepoFileResponseDTO(String path, String content, long size, String encoding) {
        this.path = path;
        this.content = content;
        this.size = size;
        this.encoding = encoding;
    }

    public static RepoFileResponseDTO ofText(String path, String content, long size) {
        return new RepoFileResponseDTO(path, content, size, "utf-8");
    }

    public static RepoFileResponseDTO ofBase64(String path, String content, long size) {
        return new RepoFileResponseDTO(path, content, size, "base64");
    }

}
