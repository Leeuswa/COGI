package idu.sba.backend.domain.repo.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

//GitHub REST API PR 변경 파일 응답 중 필요한 필드만 매핑(GET /repos/{owner}/{repo}/pulls/{n}/files)
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubPrFileDto {

    private String filename;
    private String status; //added/modified/removed/renamed
    private String patch;  //nullable — 바이너리 파일이거나 diff가 너무 크면 GitHub가 생략함

}
