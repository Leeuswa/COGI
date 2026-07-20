package idu.sba.backend.domain.pr.dto;

import idu.sba.backend.domain.pr.entity.PullRequest;
import lombok.Getter;

@Getter
public class PrDetailResponseDTO {

    private final Long id; //프론트가 pr.id로 참조(exportReview 등) — mock 데이터와도 필드명 일치시킴
    private final Long repoId;
    private final Integer githubPrNumber;
    private final String title;
    private final String authorName; //nullable — PR 작성자가 COGI 미가입자면 null
    private final String selectedModel; //nullable — API-028(모델 선택) 이번 범위 밖이라 항상 null
    private final String status;

    private PrDetailResponseDTO(Long id, Long repoId, Integer githubPrNumber, String title,
                                 String authorName, String selectedModel, String status) {
        this.id = id;
        this.repoId = repoId;
        this.githubPrNumber = githubPrNumber;
        this.title = title;
        this.authorName = authorName;
        this.selectedModel = selectedModel;
        this.status = status;
    }

    public static PrDetailResponseDTO of(PullRequest pr, String authorName) {
        return new PrDetailResponseDTO(pr.getId(), pr.getRepoId(), pr.getGithubPrNumber(), pr.getTitle(),
                authorName, pr.getSelectedModel(), pr.getStatus().name());
    }

}
