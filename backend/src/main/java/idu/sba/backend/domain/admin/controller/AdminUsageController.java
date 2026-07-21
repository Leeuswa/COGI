package idu.sba.backend.domain.admin.controller;

import idu.sba.backend.domain.admin.dto.AdminAiUsageResponseDTO;
import idu.sba.backend.domain.admin.service.AdminUsageService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

//GET /api/admin/ai-usage — ADMIN 권한은 SecurityConfig /api/admin/** 규칙이 이미 막음
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUsageController {

    private final AdminUsageService adminUsageService;

    @GetMapping("/ai-usage")
    public ApiResponse<List<AdminAiUsageResponseDTO>> getAiUsage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(adminUsageService.getAiUsage(from, to));
    }
}