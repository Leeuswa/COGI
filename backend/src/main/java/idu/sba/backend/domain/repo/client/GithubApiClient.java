package idu.sba.backend.domain.repo.client;

import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

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

}
