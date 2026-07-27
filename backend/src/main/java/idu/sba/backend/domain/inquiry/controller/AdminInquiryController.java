package idu.sba.backend.domain.inquiry.controller;

import idu.sba.backend.domain.inquiry.dto.InquiryAnswerRequestDTO;
import idu.sba.backend.domain.inquiry.dto.InquiryResponseDTO;
import idu.sba.backend.domain.inquiry.service.InquiryService;
import idu.sba.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 관리자 문의 관리 — /api/admin/** 는 SecurityConfig가 ROLE_ADMIN으로 막는다
@RestController
@RequestMapping("/api/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

    private final InquiryService inquiryService;

    // 전체 문의(최신순)
    @GetMapping
    public ApiResponse<List<InquiryResponseDTO>> list() {
        return ApiResponse.ok(inquiryService.getAllInquiries());
    }

    // 답변 등록/수정
    @PostMapping("/{id}/answer")
    public ApiResponse<InquiryResponseDTO> answer(@PathVariable Long id,
                                                  @Valid @RequestBody InquiryAnswerRequestDTO req) {
        return ApiResponse.ok("답변을 등록했어요.", inquiryService.answer(id, req.answer()));
    }
}
