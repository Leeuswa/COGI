package idu.sba.backend.domain.review.repository;

import idu.sba.backend.domain.review.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {
}
