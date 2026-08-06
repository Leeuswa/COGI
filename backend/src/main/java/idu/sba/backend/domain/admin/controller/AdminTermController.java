package idu.sba.backend.domain.admin.controller;

import idu.sba.backend.domain.admin.dto.UpdateTermRequestDTO;
import idu.sba.backend.domain.terms.dto.TermResponseDTO;
import idu.sba.backend.domain.terms.service.TermService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 관리자 약관 관리 (ADM). ADMIN 권한은 SecurityConfig /api/admin/** 규칙이 막는다.
// 조회는 공개 GET /api/terms 를 그대로 쓰고, 여기선 본문 수정만 담당.
@RestController
@RequestMapping("/api/admin/terms")
@RequiredArgsConstructor
public class AdminTermController {

    private final TermService termService;

    // 약관 목록 (본문 포함) — 편집 화면 로딩용
    @GetMapping
    public ApiResponse<List<TermResponseDTO>> getTerms() {
        return ApiResponse.ok(termService.getTerms());
    }

    // 약관 본문 수정
    @PutMapping("/{termId}")
    public ApiResponse<Void> updateTerm(@PathVariable Long termId,
                                        @RequestBody UpdateTermRequestDTO request) {
        termService.updateContent(termId, request.content());
        return ApiResponse.ok("약관이 수정되었습니다.");
    }
}
