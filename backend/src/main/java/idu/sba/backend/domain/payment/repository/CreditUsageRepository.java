package idu.sba.backend.domain.payment.repository;

import idu.sba.backend.domain.payment.entity.CreditUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface CreditUsageRepository extends JpaRepository<CreditUsage, Long> {
    Optional<CreditUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);
}
