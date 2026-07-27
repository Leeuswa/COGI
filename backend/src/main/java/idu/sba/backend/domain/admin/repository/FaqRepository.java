package idu.sba.backend.domain.admin.repository;

import idu.sba.backend.domain.admin.entity.Faq;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {
    // 공개용 — 노출 ON만, 등록순(id)
    List<Faq> findByVisibleTrueOrderByIdAsc();
    // 관리자용 — 전체, 등록순
    List<Faq> findAllByOrderByIdAsc();
}
