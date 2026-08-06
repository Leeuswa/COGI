package idu.sba.backend.domain.growth.controller;

import idu.sba.backend.domain.growth.dto.WeeklyReportIssueDTO;
import idu.sba.backend.domain.growth.dto.WeeklyReportPrDTO;
import idu.sba.backend.domain.growth.dto.WeeklyReportResponseDTO;
import idu.sba.backend.domain.growth.service.WeeklyReportService;
import idu.sba.backend.domain.growth.service.WeeklyReportSender;
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
    private final WeeklyReportSender weeklyReportSender;

    // 지난주 리포트를 지금 즉시 생성(+메일) — 월요일 배치를 안 기다리고 확인용.
    // 멱등: 지난주 리포트가 이미 있으면 스킵, 지난주 이슈 0건이어도 스킵(정상).
    @PostMapping("/generate")
    public ApiResponse<Void> generateNow(@AuthenticationPrincipal Long userId) {
        weeklyReportSender.sendFor(userId);
        return ApiResponse.ok("지난주 리포트 생성 시도 완료 (이미 있거나 지난주 이슈 0건이면 생성 안 됨)");
    }

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

    // 리포트 드릴다운 — 그 주 이슈가 걸린 PR 목록 (status=OPEN: 전체 / RESOLVED: 해결 건 있는 PR만)
    @GetMapping("/{reportId}/prs")
    public ApiResponse<List<WeeklyReportPrDTO>> reportPrs(@AuthenticationPrincipal Long userId,
                                                          @PathVariable Long reportId,
                                                          @RequestParam(defaultValue = "OPEN") String status) {
        return ApiResponse.ok(weeklyReportService.getReportPrs(userId, reportId, status));
    }

    // 리포트 드릴다운(이슈 단위) — 그 주 이슈를 하나하나 (status=OPEN: 전체 / RESOLVED: 해결된 것만)
    @GetMapping("/{reportId}/issues")
    public ApiResponse<List<WeeklyReportIssueDTO>> reportIssues(@AuthenticationPrincipal Long userId,
                                                                @PathVariable Long reportId,
                                                                @RequestParam(defaultValue = "OPEN") String status) {
        return ApiResponse.ok(weeklyReportService.getReportIssues(userId, reportId, status));
    }
}