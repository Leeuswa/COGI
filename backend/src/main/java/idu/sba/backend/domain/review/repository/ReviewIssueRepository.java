package idu.sba.backend.domain.review.repository;

import idu.sba.backend.domain.review.entity.ReviewIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReviewIssueRepository extends JpaRepository<ReviewIssue, Long> {
    List<ReviewIssue> findByReviewId(Long reviewId);

    //리뷰 히스토리 목록 [설계 추론]: 여러 리뷰의 이슈를 한 번에 조회해 N+1을 피한다
    List<ReviewIssue> findByReviewIdIn(List<Long> reviewIds);

    // 주(월요일) 단위로 묶어 발생 수 / 해결 수를 센다. WEEKDAY(월=0) 만큼 빼서 그 주 월요일로 정렬.
    @Query(value = """
    SELECT DATE(DATE_SUB(i.created_at, INTERVAL WEEKDAY(i.created_at) DAY)) AS weekStart,
           COUNT(*) AS issueCount,
           SUM(CASE WHEN i.status = 'RESOLVED' THEN 1 ELSE 0 END) AS resolvedCount
    FROM review_issues i
    JOIN reviews r ON r.id = i.review_id
    WHERE r.user_id IN (:userIds)
      AND r.target_type = 'PR'
      AND i.created_at >= :fromDate
    GROUP BY weekStart
    ORDER BY weekStart
    """, nativeQuery = true)
    List<Object[]> aggregateWeekly(@Param("userIds") List<Long> userIds,
                                   @Param("fromDate") LocalDateTime fromDate);

    // 겹쳐보기용 — 사람(user_id) × 주 단위 발생 수. 팀장이 팀원별로 나눠 볼 때 씀.
    @Query(value = """
    SELECT r.user_id AS userId,
           DATE(DATE_SUB(i.created_at, INTERVAL WEEKDAY(i.created_at) DAY)) AS weekStart,
           COUNT(*) AS issueCount
    FROM review_issues i
    JOIN reviews r ON r.id = i.review_id
    WHERE r.user_id IN (:userIds)
      AND r.target_type = 'PR'
      AND i.created_at >= :fromDate
    GROUP BY r.user_id, weekStart
    ORDER BY weekStart
    """, nativeQuery = true)
    List<Object[]> aggregateWeeklyByMember(@Param("userIds") List<Long> userIds,
                                           @Param("fromDate") LocalDateTime fromDate);

}
