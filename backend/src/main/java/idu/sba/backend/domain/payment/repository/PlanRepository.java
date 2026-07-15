package idu.sba.backend.domain.payment.repository;

import idu.sba.backend.domain.payment.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan , Long> {
    Optional<Plan> findByName(String name);
}
