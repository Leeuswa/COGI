package idu.sba.backend.domain.pr.dto;

import idu.sba.backend.domain.repo.client.GithubPrSummaryDto;
import lombok.Getter;

// Studio "PR 가져오기" 피커의 PR 목록 항목 — GitHub의 열린 PR 그대로(우리 PullRequest 테이블과는 무관,
// 웹훅으로 아직 안 들어온 PR도 보여야 하므로 GitHub API 결과를 직접 매핑)
@Getter
public class PrSummaryResponseDTO {

    private final Integer number;
    private final String title;
    private final String authorLogin; //nullable

    private PrSummaryResponseDTO(Integer number, String title, String authorLogin) {
        this.number = number;
        this.title = title;
        this.authorLogin = authorLogin;
    }

    public static PrSummaryResponseDTO of(GithubPrSummaryDto dto) {
        return new PrSummaryResponseDTO(dto.getNumber(), dto.getTitle(),
                dto.getUser() == null ? null : dto.getUser().getLogin());
    }

}
