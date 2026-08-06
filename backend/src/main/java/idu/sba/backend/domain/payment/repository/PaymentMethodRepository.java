package idu.sba.backend.domain.payment.repository;

import idu.sba.backend.domain.payment.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod , Long> {
    List<PaymentMethod> findByUserId(Long userId); //사용자가 등록한 결제수단 조회
}
