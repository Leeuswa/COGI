package idu.sba.backend.domain.repo.dto;

import idu.sba.backend.domain.repo.entity.GithubRepository;
import lombok.Getter;

@Getter
public class MyLinkedRepoResponseDTO {

    private final Long repoId;
    private final String repoName;
    private final String githubRepoId; // 프론트가 "내 GitHub 레포 목록" 중 어떤 게 이미 연동됐는지 대조할 때 씀

    private MyLinkedRepoResponseDTO(Long repoId, String repoName, String githubRepoId) {
        this.repoId = repoId;
        this.repoName = repoName;
        this.githubRepoId = githubRepoId;
    }

    public static MyLinkedRepoResponseDTO of(GithubRepository repo) {
        return new MyLinkedRepoResponseDTO(repo.getId(), repo.getRepoName(), repo.getGithubRepoId());
    }

}
