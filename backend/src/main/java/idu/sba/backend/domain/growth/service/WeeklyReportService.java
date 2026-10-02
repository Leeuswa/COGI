package idu.sba.backend.domain.growth.service;

import idu.sba.backend.domain.growth.dto.WeeklyReportIssueDTO;
import idu.sba.backend.domain.growth.dto.WeeklyReportPrDTO;
import idu.sba.backend.domain.growth.dto.WeeklyReportResponseDTO;
import idu.sba.backend.domain.growth.entity.WeeklyReport;
import idu.sba.backend.domain.growth.repository.WeeklyReportRepository;
import idu.sba.backend.domain.learning.repository.QuizSubmissionRepository;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import idu.sba.backend.global.mail.HtmlMailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WeeklyReportService {

    private final WeeklyReportRepository weeklyReportRepository;
    private final ReviewIssueRepository reviewIssueRepository;
    private final UserRepository userRepository;
    private final HtmlMailSender htmlMailSender;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM/dd");

    // 내 리포트 목록 (최신 주부터). 저장된 수치 + 카테고리 분포(조회 시 계산) + 자동 액션.
    @Transactional(readOnly = true)
    public List<WeeklyReportResponseDTO> getMyReports(Long userId) {
        return weeklyReportRepository.findByUserIdOrderByPeriodStartDesc(userId).stream()
                .map(this::toDto).toList();
    }

    // 리포트 드릴다운 — 그 주(period) 이슈가 걸린 PR 목록. status=RESOLVED면 해결 이슈가 있는 PR만.
    @Transactional(readOnly = true)
    public List<WeeklyReportPrDTO> getReportPrs(Long userId, Long reportId, String status) {
        WeeklyReport rp = findMyReport(userId, reportId);

        LocalDateTime from = rp.getPeriodStart().atStartOfDay();
        LocalDateTime to = rp.getPeriodEnd().plusDays(1).atStartOfDay(); // periodEnd(일요일) 포함
        boolean resolvedOnly = "RESOLVED".equalsIgnoreCase(status);

        return reviewIssueRepository.weeklyPrBreakdown(userId, from, to).stream()
                .map(WeeklyReportPrDTO::from)
                .filter(pr -> !resolvedOnly || pr.resolvedCount() > 0) // 해결 탭이면 해결 건 있는 PR만
                .toList();
    }

    // 리포트 드릴다운(이슈 단위) — 그 주 이슈를 하나하나. status=RESOLVED면 해결된 이슈만.
    @Transactional(readOnly = true)
    public List<WeeklyReportIssueDTO> getReportIssues(Long userId, Long reportId, String status) {
        WeeklyReport rp = findMyReport(userId, reportId);

        LocalDateTime from = rp.getPeriodStart().atStartOfDay();
        LocalDateTime to = rp.getPeriodEnd().plusDays(1).atStartOfDay();
        boolean resolvedOnly = "RESOLVED".equalsIgnoreCase(status);

        return reviewIssueRepository.weeklyIssueBreakdown(userId, from, to).stream()
                .map(WeeklyReportIssueDTO::from)
                .filter(i -> !resolvedOnly || "RESOLVED".equals(i.status())) // 해결 탭이면 해결된 이슈만
                .toList();
    }

    // 리포트 조회 + 본인 것인지 확인 (드릴다운·메일 재발송 공통)
    private WeeklyReport findMyReport(Long userId, Long reportId) {
        WeeklyReport rp = weeklyReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WEEKLY_REPORT_NOT_FOUND));
        if (!rp.getUserId().equals(userId)) throw new BusinessException(ErrorCode.WEEKLY_REPORT_ACCESS_DENIED);
        return rp;
    }

    private WeeklyReportResponseDTO toDto(WeeklyReport rp) {
        // 카테고리 분포는 저장 안 하고 그 주 범위로 다시 집계 (period: [월, 다음주 월))
        var rows = reviewIssueRepository.categoryBreakdown(
                rp.getUserId(), rp.getPeriodStart().atStartOfDay(), rp.getPeriodEnd().plusDays(1).atStartOfDay());
        List<WeeklyReportResponseDTO.Category> categories = rows.stream()
                .map(c -> new WeeklyReportResponseDTO.Category(String.valueOf(c[0]), ((Number) c[1]).intValue()))
                .toList();

        Integer prev = rp.getPrevIssueCount() > 0 ? rp.getPrevIssueCount() : null;

        // 이번 주 학습 활동 — 그 주 [월, 다음주 월) 범위의 퀴즈 제출/정답 + 생성 시점 연속 학습일
        LocalDateTime from = rp.getPeriodStart().atStartOfDay();
        LocalDateTime to = rp.getPeriodEnd().plusDays(1).atStartOfDay();
        int quizSubmits = (int) quizSubmissionRepository
                .countByUserIdAndSubmittedAtGreaterThanEqualAndSubmittedAtLessThan(rp.getUserId(), from, to);
        int quizCorrect = (int) quizSubmissionRepository
                .countByUserIdAndIsCorrectTrueAndSubmittedAtGreaterThanEqualAndSubmittedAtLessThan(rp.getUserId(), from, to);
        int correctRate = WeeklyReportTexts.percent(quizCorrect, quizSubmits);

        return new WeeklyReportResponseDTO(
                rp.getId(), rp.getPeriodStart().toString(), rp.getPeriodEnd().toString(),
                rp.getIssueCount(), rp.getResolvedCount(), prev,
                rp.getTopCategory(), categories,
                quizSubmits, correctRate, rp.getStreakEnd(),   // 실제 학습 활동
                rp.getSummary(),
                WeeklyReportTexts.nextActions(rp.getTopCategory(), rp.getIssueCount(), rp.getResolvedCount()));
    }

    // 저장된 리포트를 다시 메일로 (팝업의 메일로 보내기)
    @Transactional(readOnly = true)
    public void sendMail(Long userId, Long reportId) {
        WeeklyReport rp = findMyReport(userId, reportId);
        User u = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (u.getEmail() == null) return;

        // 자동 발송 메일과 같은 템플릿 — 카테고리 분포는 그 주 범위로 다시 집계
        var cats = reviewIssueRepository.categoryBreakdown(
                userId, rp.getPeriodStart().atStartOfDay(), rp.getPeriodEnd().plusDays(1).atStartOfDay());
        String name = u.getNickname() != null ? u.getNickname() : "회원";
        String heading = name + "님의 " + rp.getPeriodStart().format(MD) + " ~ " + rp.getPeriodEnd().format(MD) + " 성장 리포트";
        String inner = WeeklyReportMail.render(heading, rp, cats);
        htmlMailSender.send(u.getEmail(), "[COGI] 주간 성장 리포트", inner);
    }
}