package idu.sba.backend.domain.inquiry.controller;

import idu.sba.backend.domain.inquiry.dto.InquiryCreateRequestDTO;
import idu.sba.backend.domain.inquiry.dto.InquiryResponseDTO;
import idu.sba.backend.domain.inquiry.service.InquiryService;
import idu.sba.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 사용자 1:1 문의 — 로그인 필요(anyRequest authenticated)
@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    // 문의 등록
    @PostMapping
    public ApiResponse<InquiryResponseDTO> create(@AuthenticationPrincipal Long userId,
                                                  @Valid @RequestBody InquiryCreateRequestDTO req) {
        return ApiResponse.ok("문의를 등록했어요. 답변이 달리면 여기서 확인할 수 있어요.", inquiryService.create(userId, req));
    }

    // 내 문의 목록(답변 포함)
    @GetMapping("/me")
    public ApiResponse<List<InquiryResponseDTO>> myList(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(inquiryService.getMyInquiries(userId));
    }
}
