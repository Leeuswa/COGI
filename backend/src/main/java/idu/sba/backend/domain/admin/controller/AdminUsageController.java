package idu.sba.backend.domain.admin.controller;

import idu.sba.backend.domain.admin.dto.AdminAiUsageResponseDTO;
import idu.sba.backend.domain.admin.dto.ProviderUsageResponseDTO;
import idu.sba.backend.domain.admin.service.AdminUsageService;
import idu.sba.backend.global.ai.ProviderUsageClient;
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
    private final ProviderUsageClient providerUsageClient;

    @GetMapping("/ai-usage")
    public ApiResponse<List<AdminAiUsageResponseDTO>> getAiUsage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(adminUsageService.getAiUsage(from, to));
    }

    // 벤더 대시보드 실사용량 (OpenAI/Anthropic Admin API). Admin 키 미설정이면 빈 배열.
    @GetMapping("/ai-usage/provider")
    public ApiResponse<List<ProviderUsageResponseDTO>> getProviderUsage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(providerUsageClient.fetch(from, to));
    }
}