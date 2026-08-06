package idu.sba.backend.domain.repo.dto;

import idu.sba.backend.domain.repo.client.GithubRepoDto;
import lombok.Getter;

@Getter
public class GithubRepoResponseDTO {

    private final String githubRepoId;
    private final String repoName;
    private final boolean isPrivate;

    private GithubRepoResponseDTO(String githubRepoId, String repoName, boolean isPrivate){
        this.githubRepoId = githubRepoId;
        this.repoName = repoName;
        this.isPrivate = isPrivate;
    }

    public static GithubRepoResponseDTO of(GithubRepoDto repo){
        return new GithubRepoResponseDTO(String.valueOf(repo.getId()), repo.getName(), repo.isPrivate());
    }

}
