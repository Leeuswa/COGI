package idu.sba.backend.domain.terms.controller;

import idu.sba.backend.domain.terms.dto.TermResponseDTO;
import idu.sba.backend.domain.terms.service.TermService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/terms")
@RequiredArgsConstructor
public class TermController {

    private final TermService termService;

    // API-063: 약관 목록 조회 (가입 화면 체크박스용)
    @GetMapping
    public ApiResponse<List<TermResponseDTO>> getTerms() {
        return ApiResponse.ok(termService.getTerms());
    }
}