package idu.sba.backend.domain.repo.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

//GitHub REST API 파일 내용 응답 중 필요한 필드만 매핑(GET /repos/{owner}/{repo}/contents/{path})
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubContentDto {

    private String path;
    private long size;
    private String content;  //base64 — 1MB 넘는 파일은 GitHub가 아예 생략함(null)
    private String encoding; //보통 "base64"
    private String type;     //"file" / "dir" — 디렉터리면 content가 없음

}
