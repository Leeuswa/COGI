package idu.sba.backend.domain.growth.service;

import idu.sba.backend.domain.growth.dto.WeeklyReportResponseDTO;
import idu.sba.backend.domain.growth.entity.WeeklyReport;
import idu.sba.backend.domain.growth.repository.WeeklyReportRepository;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import idu.sba.backend.global.mail.HtmlMailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WeeklyReportService {

    private final WeeklyReportRepository weeklyReportRepository;
    private final ReviewIssueRepository reviewIssueRepository;
    private final UserRepository userRepository;
    private final HtmlMailSender htmlMailSender;

    // 내 리포트 목록 (최신 주부터). 저장된 수치 + 카테고리 분포(조회 시 계산) + 자동 액션.
    @Transactional(readOnly = true)
    public List<WeeklyReportResponseDTO> getMyReports(Long userId) {
        return weeklyReportRepository.findByUserIdOrderByPeriodStartDesc(userId).stream()
                .map(this::toDto).toList();
    }

    private WeeklyReportResponseDTO toDto(WeeklyReport rp) {
        // 카테고리 분포는 저장 안 하고 그 주 범위로 다시 집계 (period: [월, 다음주 월))
        var rows = reviewIssueRepository.categoryBreakdown(
                rp.getUserId(), rp.getPeriodStart().atStartOfDay(), rp.getPeriodEnd().plusDays(1).atStartOfDay());
        List<WeeklyReportResponseDTO.Category> categories = rows.stream()
                .map(c -> new WeeklyReportResponseDTO.Category(String.valueOf(c[0]), ((Number) c[1]).intValue()))
                .toList();

        Integer prev = rp.getPrevIssueCount() > 0 ? rp.getPrevIssueCount() : null;

        return new WeeklyReportResponseDTO(
                rp.getId(), rp.getPeriodStart().toString(), rp.getPeriodEnd().toString(),
                rp.getIssueCount(), rp.getResolvedCount(), prev,
                rp.getTopCategory(), categories,
                0, 0, 0,                 // 퀴즈/정답률/연속 — MVP 기본값
                rp.getSummary(), buildActions(rp));
    }

    private List<String> buildActions(WeeklyReport rp) {
        List<String> a = new ArrayList<>();
        if (rp.getTopCategory() != null) a.add(rp.getTopCategory() + " 유형 학습카드를 복습해보세요.");
        if (rp.getResolvedCount() < rp.getIssueCount()) a.add("미해결 이슈를 스튜디오에서 마저 판정해보세요.");
        a.add("이번 주도 PR을 올려 리뷰를 받아보세요.");
        return a;
    }

    // 저장된 리포트를 다시 메일로 (팝업의 메일로 보내기)
    @Transactional(readOnly = true)
    public void sendMail(Long userId, Long reportId) {
        WeeklyReport rp = weeklyReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        if (!rp.getUserId().equals(userId)) throw new BusinessException(ErrorCode.INVALID_INPUT); // 남의 리포트 차단
        User u = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (u.getEmail() == null) return;

        int rate = rp.getIssueCount() == 0 ? 0 : (int) Math.round(rp.getResolvedCount() * 100.0 / rp.getIssueCount());
        String name = u.getNickname() != null ? u.getNickname() : "회원";
        String inner = """
            <p style="margin:0 0 8px;color:#1b2a4a;font-size:17px;font-weight:bold;">%s님의 주간 성장 리포트</p>
            <p style="margin:0 0 16px;color:#40507a;font-size:13px;">%s</p>
            <div style="background:#ffd23f;border:3px solid #1b2a4a;padding:16px;text-align:center;color:#1b2a4a;font-size:14px;">
              발생 %d건 · 해결 %d건 · 해결률 %d%%
            </div>
            """.formatted(name, rp.getSummary(), rp.getIssueCount(), rp.getResolvedCount(), rate);
        htmlMailSender.send(u.getEmail(), "[COGI] 주간 성장 리포트", inner);
    }
}