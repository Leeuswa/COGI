package idu.sba.backend.domain.review.service;

public interface ReviewIssueService {

    // [설계 추론] 스튜디오 판정 확정 — 의도(IGNORED)는 바로 반영(요구 #7).
    // 고침(RESOLVED)은 PR 리뷰의 CRITICAL 이슈면 팀장 승인 대기(PENDING)로, 그 외엔 바로 반영.
    void finalizeIssue(Long currentUserId, Long issueId, String verdict);

    // 이슈 승인 흐름(RDB-003 [설계 추론]) — 레포 팀장만 호출 가능. approve=false면 반려(OPEN으로 되돌림)
    void decideResolveRequest(Long approverId, Long issueId, boolean approve);

}
