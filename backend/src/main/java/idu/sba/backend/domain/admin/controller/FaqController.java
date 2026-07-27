package idu.sba.backend.domain.admin.controller;

import idu.sba.backend.domain.admin.dto.FaqResponseDTO;
import idu.sba.backend.domain.admin.service.FaqService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 공개 FAQ 조회 (로그인 불필요 — SecurityConfig permitAll)
@RestController
@RequestMapping("/api/faqs")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    @GetMapping
    public ApiResponse<List<FaqResponseDTO>> list() {
        return ApiResponse.ok(faqService.getPublicFaqs());
    }
}
