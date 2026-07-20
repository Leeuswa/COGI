package idu.sba.backend.domain.review.service;

public interface ReviewIssueService {

    // [설계 추론] 스튜디오 판정 확정 — 의도(IGNORED)/고침(RESOLVED)을 팀장 승인 없이 바로 반영(요구 #7)
    void finalizeIssue(Long currentUserId, Long issueId, String verdict);

}
