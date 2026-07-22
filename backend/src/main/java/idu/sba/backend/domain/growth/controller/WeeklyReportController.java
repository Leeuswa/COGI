package idu.sba.backend.domain.growth.controller;

import idu.sba.backend.domain.growth.dto.WeeklyReportResponseDTO;
import idu.sba.backend.domain.growth.service.WeeklyReportService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/weekly-reports")
@RequiredArgsConstructor
public class WeeklyReportController {

    private final WeeklyReportService weeklyReportService;

    // 내 주간 리포트 목록 (최신 주부터)
    @GetMapping
    public ApiResponse<List<WeeklyReportResponseDTO>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(weeklyReportService.getMyReports(userId));
    }

    // 저장된 리포트를 다시 메일로 발송 (팝업의 "메일로 보내기")
    @PostMapping("/{reportId}/send-mail")
    public ApiResponse<Void> sendMail(@AuthenticationPrincipal Long userId, @PathVariable Long reportId) {
        weeklyReportService.sendMail(userId, reportId);
        return ApiResponse.ok("메일로 보냈어요.");
    }
}