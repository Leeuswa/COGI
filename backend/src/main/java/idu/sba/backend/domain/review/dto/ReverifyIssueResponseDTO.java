package idu.sba.backend.domain.review.dto;

import lombok.Getter;

//이슈 재검증 [설계 추론] 응답 — 참고용 판정일 뿐, 이슈 상태(status)는 이걸로 안 바뀐다.
//실제 RESOLVED/IGNORED 전환은 여전히 기존 finalizeIssue(판정 버튼)를 거쳐야 한다.
@Getter
public class ReverifyIssueResponseDTO {

    private final boolean stillPresent;
    private final String explanation;

    private ReverifyIssueResponseDTO(boolean stillPresent, String explanation) {
        this.stillPresent = stillPresent;
        this.explanation = explanation;
    }

    public static ReverifyIssueResponseDTO of(boolean stillPresent, String explanation) {
        return new ReverifyIssueResponseDTO(stillPresent, explanation);
    }

}
