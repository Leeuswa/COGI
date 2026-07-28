package idu.sba.backend.domain.repo.client;

import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

//미리보기가 참조하는 레포 파일(CSS/JS/이미지) 원문 조회 전용 — GithubApiClient와 별개 클라이언트
//(GithubApiClient는 다른 담당자 소유라 새 메서드를 얹지 않고 따로 둔다)
@Slf4j
@Component
public class GithubFileClient {

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://api.github.com")
            .build();

    //레포의 파일 하나를 조회(GET /repos/{owner}/{repo}/contents/{path})
    public GithubContentDto getFileContent(String accessToken, String repoFullName, String path) {
        String[] parts = repoFullName.split("/", 2);
        try {
            return restClient.get()
                    .uri(buildContentsUri(parts[0], parts[1], path))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(GithubContentDto.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND);
            }
            log.error("파일 조회 실패 - repoFullName={}, path={}, status={}, body={}",
                    repoFullName, path, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.GITHUB_API_ERROR);
        }
    }

    //path의 '/'를 그대로 경로 구분자로 살리면서 각 구간만 인코딩 — 템플릿 변수 하나로 넘기면
    //GithubApiClient의 owner/repo 경우처럼 '/'가 %2F로 통째로 인코딩돼버린다
    private URI buildContentsUri(String owner, String repo, String path) {
        String encodedPath = Arrays.stream(path.split("/"))
                .map(seg -> UriUtils.encodePathSegment(seg, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
        return URI.create("https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + encodedPath);
    }

}
