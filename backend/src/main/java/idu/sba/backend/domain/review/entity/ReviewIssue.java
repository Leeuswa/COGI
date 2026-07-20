package idu.sba.backend.domain.review.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_issues")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reviewId;

    @Enumerated(EnumType.STRING)
    private IssueCategory category;

    @Enumerated(EnumType.STRING)
    private IssueSeverity severity;

    private String filePath; //nullable
    private Integer lineNumber; //nullable

    @Lob
    private String description;

    @Enumerated(EnumType.STRING)
    private IssueStatus status;

    private boolean acknowledged;

    private String requestType; //nullable — RESOLVE_REQUEST/IGNORE_REQUEST, 승인 흐름(RDB-003)은 범위 밖이라 필드만 둠
    private Long approvedBy; //nullable

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate(){
        this.updatedAt = LocalDateTime.now();
    }

    private ReviewIssue(Long reviewId, IssueCategory category, IssueSeverity severity,
                         String filePath, Integer lineNumber, String description){
        this.reviewId = reviewId;
        this.category = category;
        this.severity = severity;
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.description = description;
        this.status = IssueStatus.OPEN;
        this.acknowledged = false;
    }

    public static ReviewIssue of(Long reviewId, IssueCategory category, IssueSeverity severity,
                                  String filePath, Integer lineNumber, String description){
        return new ReviewIssue(reviewId, category, severity, filePath, lineNumber, description);
    }

    // 스튜디오 판정 확정(RESOLVED/IGNORED) — 팀장 승인 없이 바로 반영(요구 #7).
    // acknowledged(통계 제외)는 "의도한 코드"(IGNORED)일 때만 true — RESOLVED는 고치겠다는 뜻이라
    // 실제 약점 패턴이 맞으므로 통계에서 빼면 안 됨(예전엔 둘 다 true로 잘못 설정하던 버그)
    // requestType/approvedBy는 안 건드림 — 승인 흐름(RDB-003)이 실제로 생기면 그때 채울 필드
    public void finalizeAs(IssueStatus verdict){
        this.status = verdict;
        this.acknowledged = (verdict == IssueStatus.IGNORED);
    }

}
