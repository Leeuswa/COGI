package idu.sba.backend.domain.pr.entity;

public enum PullRequestStatus {

    OPEN,      //대기/리뷰 진행중
    REVIEWED,  //리뷰 완료
    FAILED     //리뷰 실패, 재시도 대상(webhookRetryCount)

}
