package idu.sba.backend.domain.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

//GitHub pull_request 웹훅 이벤트 payload 중 필요한 필드만 매핑(나머지는 무시)
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubPullRequestEventPayload(
        String action, //opened/synchronize/reopened/closed 등
        Integer number, //GitHub PR 번호
        @JsonProperty("pull_request") GithubPrPayload pullRequest,
        GithubRepoPayload repository
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GithubPrPayload(Integer number, String title, GithubUserPayload user) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GithubUserPayload(String login) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GithubRepoPayload(Long id, @JsonProperty("full_name") String fullName) {}

}
