package idu.sba.backend.domain.pr.dto;

import idu.sba.backend.domain.pr.entity.PullRequest;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PrDetailResponseDTO {

    private final Long id; //프론트가 pr.id로 참조(exportReview 등) — mock 데이터와도 필드명 일치시킴
    private final Long repoId;
    private final Integer githubPrNumber;
    private final String title;
    private final String authorName; //nullable — PR 작성자가 COGI 미가입자면 null
    private final String selectedModel; //nullable — API-028(모델 선택) 이번 범위 밖이라 항상 null
    private final String status;
    private final LocalDateTime createdAt; //프론트 PrDetail.tsx가 헤더에 표시(pr.createdAt)
    private final String myRole; //OWNER/MEMBER — 이슈 승인 버튼 노출 여부 판단용(mock에서 이미 쓰던 필드, API-034)

    private PrDetailResponseDTO(Long id, Long repoId, Integer githubPrNumber, String title,
                                 String authorName, String selectedModel, String status,
                                 LocalDateTime createdAt, String myRole) {
        this.id = id;
        this.repoId = repoId;
        this.githubPrNumber = githubPrNumber;
        this.title = title;
        this.authorName = authorName;
        this.selectedModel = selectedModel;
        this.status = status;
        this.createdAt = createdAt;
        this.myRole = myRole;
    }

    public static PrDetailResponseDTO of(PullRequest pr, String authorName, String myRole) {
        return new PrDetailResponseDTO(pr.getId(), pr.getRepoId(), pr.getGithubPrNumber(), pr.getTitle(),
                authorName, pr.getSelectedModel(), pr.getStatus().name(), pr.getCreatedAt(), myRole);
    }

}
