package idu.sba.backend.domain.terms.service;

import idu.sba.backend.domain.terms.dto.TermResponseDTO;
import java.util.List;

public interface TermService {
    List<TermResponseDTO> getTerms();

    // 관리자 약관 본문 수정
    void updateContent(Long termId, String content);
}