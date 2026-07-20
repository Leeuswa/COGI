package idu.sba.backend.domain.terms.service;

import idu.sba.backend.domain.terms.dto.TermResponseDTO;
import java.util.List;

public interface TermService {
    List<TermResponseDTO> getTerms();
}