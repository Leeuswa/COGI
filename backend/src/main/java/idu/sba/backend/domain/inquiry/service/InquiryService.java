package idu.sba.backend.domain.inquiry.service;

import idu.sba.backend.domain.inquiry.dto.InquiryCreateRequestDTO;
import idu.sba.backend.domain.inquiry.dto.InquiryResponseDTO;
import idu.sba.backend.domain.inquiry.entity.Inquiry;
import idu.sba.backend.domain.inquiry.repository.InquiryRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final UserRepository userRepository;

    // 사용자 — 문의 등록 (작성자 이메일/닉네임 스냅샷 저장)
    @Transactional
    public InquiryResponseDTO create(Long userId, InquiryCreateRequestDTO req) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Inquiry i = inquiryRepository.save(
                Inquiry.create(userId, u.getEmail(), u.getNickname(), req.title(), req.content()));
        return InquiryResponseDTO.of(i);
    }

    // 사용자 — 내 문의 목록
    @Transactional(readOnly = true)
    public List<InquiryResponseDTO> getMyInquiries(Long userId) {
        return inquiryRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(InquiryResponseDTO::of).toList();
    }

    // 관리자 — 전체 문의
    @Transactional(readOnly = true)
    public List<InquiryResponseDTO> getAllInquiries() {
        return inquiryRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(InquiryResponseDTO::of).toList();
    }

    // 관리자 — 답변 등록/수정
    @Transactional
    public InquiryResponseDTO answer(Long inquiryId, String answer) {
        Inquiry i = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        i.answer(answer); // 더티체킹으로 자동 UPDATE
        return InquiryResponseDTO.of(i);
    }
}
