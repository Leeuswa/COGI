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

    // 주간 리포트용 — 한 사용자의 기간 내 발생/해결 수 (한 행)
    @Query(value = """
    SELECT COUNT(*) AS issueCount,
           SUM(CASE WHEN i.status = 'RESOLVED' THEN 1 ELSE 0 END) AS resolvedCount
    FROM review_issues i
    JOIN reviews r ON r.id = i.review_id
    WHERE r.user_id = :userId AND r.target_type = 'PR'
      AND i.created_at >= :from AND i.created_at < :to
    """, nativeQuery = true)
    List<Object[]> weeklySummary(@Param("userId") Long userId,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);


    // 카테고리별 발생 수 (주간 리포트의 최다 카테고리 / 분포 바 용). count 많은 순
    @Query(value = """
    SELECT i.category AS category, COUNT(*) AS cnt
    FROM review_issues i
    JOIN reviews r ON r.id = i.review_id
    WHERE r.user_id = :userId AND r.target_type = 'PR'
      AND i.created_at >= :from AND i.created_at < :to
    GROUP BY i.category
    ORDER BY cnt DESC
    """, nativeQuery = true)
    List<Object[]> categoryBreakdown(@Param("userId") Long userId,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

}
