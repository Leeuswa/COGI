package idu.sba.backend.domain.inquiry.repository;

import idu.sba.backend.domain.inquiry.entity.Inquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
    // 내 문의 (최신순)
    List<Inquiry> findByUserIdOrderByCreatedAtDesc(Long userId);
    // 관리자 — 전체 (최신순)
    List<Inquiry> findAllByOrderByCreatedAtDesc();
}
