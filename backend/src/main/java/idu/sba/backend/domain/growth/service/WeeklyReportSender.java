package idu.sba.backend.domain.growth.service;

import idu.sba.backend.domain.growth.entity.WeeklyReport;
import idu.sba.backend.domain.growth.repository.WeeklyReportRepository;
import idu.sba.backend.domain.retention.repository.UserStreakRepository;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.entity.UserStatus;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.mail.HtmlMailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WeeklyReportSender {

    private final UserRepository userRepository;
    private final ReviewIssueRepository reviewIssueRepository;
    private final WeeklyReportRepository weeklyReportRepository;
    private final HtmlMailSender htmlMailSender;
    private final UserStreakRepository userStreakRepository;

    // 한 사용자의 "지난주(월~일)" 리포트를 저장 + 메일 발송. 활동 없거나 이미 생성됐으면 스킵.
    @Transactional
    public void sendFor(Long userId) {
        User u = userRepository.findById(userId).orElse(null);
        if (u == null || u.getStatus() != UserStatus.ACTIVE) return; // 수동 생성(/generate)도 같은 기준

        LocalDate thisMonday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate lastMonday = thisMonday.minusWeeks(1);
        if (weeklyReportRepository.existsByUserIdAndPeriodStart(userId, lastMonday)) return; // 중복 방지

        LocalDateTime from = lastMonday.atStartOfDay();                    // 지난주 월
        LocalDateTime to = thisMonday.atStartOfDay();                      // 지난주 끝(=이번주 월)
        LocalDateTime prevFrom = lastMonday.minusWeeks(1).atStartOfDay();  // 전전주 월

        long[] cur = summary(userId, from, to);
        int issues = (int) cur[0];
        if (issues == 0) return;                                          // 지난주 활동 없으면 생략
        int resolved = (int) cur[1];
        int prevIssues = (int) summary(userId, prevFrom, from)[0];

        List<Object[]> cats = reviewIssueRepository.categoryBreakdown(userId, from, to);
        String topCategory = cats.isEmpty() ? null : String.valueOf(cats.get(0)[0]);

        int rate = WeeklyReportTexts.percent(resolved, issues);
        String summary = buildSummary(issues, resolved, prevIssues, rate);

        // 저장 (주간리포트 탭에서 목록으로 보임)
        WeeklyReport report = WeeklyReport.of(
                userId, lastMonday, thisMonday.minusDays(1), issues, resolved, prevIssues, topCategory, summary);
        report.recordStreakEnd(userStreakRepository.findByUserId(userId)
                .map(s -> s.effectiveStreak(LocalDate.now())).orElse(0));
        weeklyReportRepository.save(report);

        // 2) 메일 발송 (이메일 있을 때만)
        if (u.getEmail() != null) {
            String name = u.getNickname() != null ? u.getNickname() : "회원";
            String inner = WeeklyReportMail.render(name + "님의 지난주 성장 리포트", report, cats);
            htmlMailSender.send(u.getEmail(), "[COGI] 지난주 성장 리포트", inner);
        }
    }

    // weeklySummary는 항상 1행 → [발생, 해결]
    private long[] summary(Long userId, LocalDateTime from, LocalDateTime to) {
        Object[] r = reviewIssueRepository.weeklySummary(userId, from, to).get(0);
        long issues = r[0] == null ? 0 : ((Number) r[0]).longValue();
        long resolved = r[1] == null ? 0 : ((Number) r[1]).longValue();
        return new long[]{ issues, resolved };
    }

    private String buildSummary(int issues, int resolved, int prevIssues, int rate) {
        String trend = prevIssues == 0 ? ""
                : issues < prevIssues ? " 전주보다 줄었어요!"
                : issues > prevIssues ? " 전주보다 늘었어요."
                : " 전주와 비슷해요.";
        return "지난주 발생 " + issues + "건 중 " + resolved + "건 해결(해결률 " + rate + "%)." + trend;
    }
}
