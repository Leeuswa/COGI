package idu.sba.backend.domain.review.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

// 이슈 승인 흐름(RDB-003 [설계 추론]) — 팀장이 RESOLVE_REQUEST(PENDING)를 승인/반려할 때 쓰는 바디
@Getter
@NoArgsConstructor
public class IssueDecisionRequestDTO {

    private boolean approve; // true=승인(RESOLVED), false=반려(OPEN으로 되돌림)

}
