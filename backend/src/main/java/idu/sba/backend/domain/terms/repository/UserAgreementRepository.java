package idu.sba.backend.domain.terms.repository;

import idu.sba.backend.domain.terms.entity.UserAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAgreementRepository extends JpaRepository<UserAgreement,Long> {

}
