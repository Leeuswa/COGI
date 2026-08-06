package idu.sba.backend.domain.review.repository;

import idu.sba.backend.domain.review.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    //기간 조회 , 최신순 (관리자 AI 사용량 표시)
    List<AiUsageLog>findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start , LocalDateTime end);
}
