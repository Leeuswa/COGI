package idu.sba.backend.domain.terms.service;

import idu.sba.backend.domain.terms.dto.TermResponseDTO;
import idu.sba.backend.domain.terms.repository.TermRepository;
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
}