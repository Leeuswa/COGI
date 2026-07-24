package idu.sba.backend.domain.retention.controller;

import idu.sba.backend.domain.retention.dto.RetentionStatusResponseDTO;
import idu.sba.backend.domain.retention.service.RetentionService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class RetentionController {

    private final RetentionService retentionService;

    // API-051: 현재 streak·오늘 제출 여부 조회
    @GetMapping("/retention-status")
    public ApiResponse<RetentionStatusResponseDTO> getRetentionStatus(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(retentionService.getRetentionStatus(userId));
    }
}
