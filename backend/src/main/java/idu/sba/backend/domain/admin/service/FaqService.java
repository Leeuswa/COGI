package idu.sba.backend.domain.admin.service;

import idu.sba.backend.domain.admin.dto.FaqRequestDTO;
import idu.sba.backend.domain.admin.dto.FaqResponseDTO;
import idu.sba.backend.domain.admin.entity.Faq;
import idu.sba.backend.domain.admin.repository.FaqRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FaqService {

    private final FaqRepository faqRepository;

    // 공개 — 노출 ON만
    @Transactional(readOnly = true)
    public List<FaqResponseDTO> getPublicFaqs() {
        return faqRepository.findByVisibleTrueOrderByIdAsc()
                .stream().map(FaqResponseDTO::of).toList();
    }

    // 관리자 — 전체
    @Transactional(readOnly = true)
    public List<FaqResponseDTO> getAllFaqs() {
        return faqRepository.findAllByOrderByIdAsc()
                .stream().map(FaqResponseDTO::of).toList();
    }

    @Transactional
    public FaqResponseDTO create(FaqRequestDTO req) {
        Faq f = faqRepository.save(
                Faq.create(req.question(), req.answer(), req.category(), req.visible()));
        return FaqResponseDTO.of(f);
    }

    @Transactional
    public FaqResponseDTO update(Long id, FaqRequestDTO req) {
        Faq f = faqRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        f.update(req.question(), req.answer(), req.category(), req.visible());
        return FaqResponseDTO.of(f); // 더티체킹으로 자동 UPDATE
    }

    @Transactional
    public void delete(Long id) {
        faqRepository.deleteById(id);
    }
}
