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
    public record GithubPrPayload(Integer number, String title, GithubUserPayload user, GithubRefPayload head) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GithubUserPayload(String login) {}

    //웹훅 재전송(redelivery)·재생 판별용 — 같은 커밋(sha)에 대한 이벤트가 중복 도착해도 리뷰를 또 만들지 않기 위함
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GithubRefPayload(String sha) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GithubRepoPayload(Long id, @JsonProperty("full_name") String fullName) {}

}
