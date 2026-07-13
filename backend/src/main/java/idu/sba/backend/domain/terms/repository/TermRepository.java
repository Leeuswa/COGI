package idu.sba.backend.domain.terms.repository;

import idu.sba.backend.domain.terms.entity.Term;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TermRepository extends JpaRepository<Term,Long> {

    //가입 시 필수 약관 동의 했는지 확인
    List<Term> findByIsRequiredTrue();
}
