package idu.sba.backend.domain.repo.dto;

import idu.sba.backend.domain.repo.entity.GithubRepository;
import lombok.Getter;

@Getter
public class RepoLinkResponseDTO {

    private final Long repoId;
    private final String repoName;
    private final String webhookId; //Webhook 수신 작업 전까지는 항상 null

    private RepoLinkResponseDTO(Long repoId, String repoName, String webhookId){
        this.repoId = repoId;
        this.repoName = repoName;
        this.webhookId = webhookId;
    }

    public static RepoLinkResponseDTO of(GithubRepository repo){
        return new RepoLinkResponseDTO(repo.getId(), repo.getRepoName(), repo.getWebhookId());
    }

}
