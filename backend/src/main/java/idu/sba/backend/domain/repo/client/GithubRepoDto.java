package idu.sba.backend.domain.repo.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

//GitHub REST API 레포 응답 중 필요한 필드만 매핑(나머지는 무시)
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubRepoDto {

    private Long id;
    private String name;

    @JsonProperty("private")
    private boolean isPrivate;

    @JsonProperty("full_name")
    private String fullName; //"owner/repo" 형태 — PR 파일/diff 조회(API-024/025)에 필요

}
