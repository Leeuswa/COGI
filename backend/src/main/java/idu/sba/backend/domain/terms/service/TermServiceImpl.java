package idu.sba.backend.domain.terms.service;

import idu.sba.backend.domain.terms.dto.TermResponseDTO;
import idu.sba.backend.domain.terms.entity.Term;
import idu.sba.backend.domain.terms.repository.TermRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TermServiceImpl implements TermService {

    private final TermRepository termRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TermResponseDTO> getTerms() {
        return termRepository.findAllByOrderByIdAsc().stream()
                .map(TermResponseDTO::from)
                .toList();
    }

    @Override
    @Transactional
    public void updateContent(Long termId, String content) {
        Term term = termRepository.findById(termId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TERM_NOT_FOUND));
        term.updateContent(content);
    }
}