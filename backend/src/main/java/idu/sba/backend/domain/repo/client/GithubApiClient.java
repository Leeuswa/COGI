package idu.sba.backend.domain.repo.client;

import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Slf4j
@Component
public class GithubApiClient {

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://api.github.com")
            .build();

    //내 GitHub 레포 목록 조회(소유·협업 레포 포함, private 포함 — API-022)
    public List<GithubRepoDto> listRepos(String accessToken) {
        try {
            GithubRepoDto[] repos = restClient.get()
                    .uri("/user/repos?per_page=100&affiliation=owner,collaborator")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(GithubRepoDto[].class);
            return repos != null ? List.of(repos) : List.of();
        } catch (RestClientResponseException e) {
            throw new BusinessException(ErrorCode.GITHUB_API_ERROR);
        }
    }

    //레포 ID로 단건 조회(연동 시점의 최신 이름/공개여부 확인용 — API-023)
    public GithubRepoDto getRepo(String accessToken, String githubRepoId) {
        try {
            return restClient.get()
                    .uri("/repositories/{id}", githubRepoId)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(GithubRepoDto.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND);
            }
            throw new BusinessException(ErrorCode.GITHUB_API_ERROR);
        }
    }

    //PR의 변경 파일 + diff(patch) 조회(API-024/025) — 100개 초과 파일은 페이지네이션 필요하나 이번 범위는 첫 페이지만
    public List<GithubPrFileDto> listPrFiles(String accessToken, String repoFullName, int prNumber) {
        // repoFullName("owner/repo")을 {fullName} 하나로 넘기면 Spring이 그 안의 '/'를 %2F로 인코딩해버려서
        // GitHub가 존재하지 않는 경로로 받는다 — owner/repo를 각자 별도 경로 세그먼트로 넘겨야 한다.
        String[] parts = repoFullName.split("/", 2);
        try {
            GithubPrFileDto[] files = restClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{prNumber}/files?per_page=100", parts[0], parts[1], prNumber)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(GithubPrFileDto[].class);
            return files != null ? List.of(files) : List.of();
        } catch (RestClientResponseException e) {
            log.error("PR 파일 조회 실패 - repoFullName={}, prNumber={}, status={}, body={}",
                    repoFullName, prNumber, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.GITHUB_API_ERROR);
        }
    }

}
