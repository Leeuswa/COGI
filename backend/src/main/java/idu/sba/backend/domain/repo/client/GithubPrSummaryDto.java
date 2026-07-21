package idu.sba.backend.domain.repo.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

//GitHub REST API PR 목록 응답 중 필요한 필드만 매핑(GET /repos/{owner}/{repo}/pulls) — Studio "PR 가져오기" 피커용
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubPrSummaryDto {

    private Integer number;
    private String title;
    private GithubPrUserDto user; //nullable

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GithubPrUserDto {
        private String login;
    }

}
