package idu.sba.backend.domain.payment.repository;

import idu.sba.backend.domain.payment.entity.CreditUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface CreditUsageRepository extends JpaRepository<CreditUsage, Long> {
    Optional<CreditUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);

    // 크레딧 소모(체크 후 차감)의 동시성 경합을 막기 위한 락 조회 — 같은 사용자의 오늘 행을 동시에
    // 두 요청이 처리하면 락 없이는 둘 다 한도 통과를 봐버려서(lost update) 한도를 넘겨 차감될 수 있음.
    // 파생 쿼리 이름에 "ForUpdate" 접미사를 붙이면 파서가 존재하지 않는 프로퍼티로 오인하므로 @Query로 명시
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CreditUsage c where c.userId = :userId and c.usageDate = :usageDate")
    Optional<CreditUsage> findByUserIdAndUsageDateForUpdate(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);
}
