package idu.sba.backend.domain.admin.controller;

import idu.sba.backend.domain.admin.dto.FaqRequestDTO;
import idu.sba.backend.domain.admin.dto.FaqResponseDTO;
import idu.sba.backend.domain.admin.service.FaqService;
import idu.sba.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 관리자 FAQ CRUD — /api/admin/** 는 SecurityConfig가 ROLE_ADMIN으로 막는다
@RestController
@RequestMapping("/api/admin/faqs")
@RequiredArgsConstructor
public class AdminFaqController {

    private final FaqService faqService;

    @GetMapping
    public ApiResponse<List<FaqResponseDTO>> list() {
        return ApiResponse.ok(faqService.getAllFaqs());
    }

    @PostMapping
    public ApiResponse<FaqResponseDTO> create(@Valid @RequestBody FaqRequestDTO req) {
        return ApiResponse.ok("FAQ를 추가했습니다.", faqService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<FaqResponseDTO> update(@PathVariable Long id, @Valid @RequestBody FaqRequestDTO req) {
        return ApiResponse.ok("FAQ를 수정했습니다.", faqService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        faqService.delete(id);
        return ApiResponse.ok("FAQ를 삭제했습니다.");
    }
}

// pr테스트
