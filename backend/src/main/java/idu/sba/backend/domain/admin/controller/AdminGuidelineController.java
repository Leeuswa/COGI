package idu.sba.backend.domain.admin.controller;

import idu.sba.backend.domain.admin.dto.SaveGuidelineRequestDTO;
import idu.sba.backend.domain.admin.service.AdminGuidelineService;
import idu.sba.backend.domain.user.entity.Level;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// 관리자 수준별 리뷰 지침 (ADM-004). ADMIN 권한은 SecurityConfig /api/admin/** 규칙이 막는다.
@RestController
@RequestMapping("/api/admin/review-guidelines")
@RequiredArgsConstructor
public class AdminGuidelineController {

    private final AdminGuidelineService adminGuidelineService;

    // API-020 지침 조회
    @GetMapping
    public ApiResponse<Map<String, String>> getGuidelines() {
        return ApiResponse.ok(adminGuidelineService.getGuidelines());
    }

    // API-021 지침 저장 — path의 "BEGINNER" 등이 Level enum으로 자동 변환(잘못된 값이면 400)
    @PutMapping("/{level}")
    public ApiResponse<Void> saveGuideline(@PathVariable Level level,
                                           @RequestBody SaveGuidelineRequestDTO request) {
        adminGuidelineService.saveGuideline(level, request.promptGuideline());
        return ApiResponse.ok("지침이 저장되었습니다.");
    }
}
