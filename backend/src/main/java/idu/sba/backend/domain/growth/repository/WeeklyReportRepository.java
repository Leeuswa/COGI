package idu.sba.backend.domain.growth.repository;

import idu.sba.backend.domain.growth.entity.WeeklyReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport,Long> {

    //내 리포트 최신 주부터
    List<WeeklyReport> findByUserIdOrderByPeriodStartDesc(Long userId);

    boolean existsByUserIdAndPeriodStart(Long userId, java.time.LocalDate periodStart);

}
