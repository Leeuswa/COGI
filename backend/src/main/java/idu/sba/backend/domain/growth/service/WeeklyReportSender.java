package idu.sba.backend.domain.growth.service;

import idu.sba.backend.domain.growth.entity.WeeklyReport;
import idu.sba.backend.domain.growth.repository.WeeklyReportRepository;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.user.entity.User;
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

    // 한 사용자의 "지난주(월~일)" 리포트를 저장 + 메일 발송. 활동 없거나 이미 생성됐으면 스킵.
    @Transactional
    public void sendFor(Long userId) {
        User u = userRepository.findById(userId).orElse(null);
        if (u == null) return;

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

        int rate = (int) Math.round(resolved * 100.0 / issues);
        String summary = buildSummary(issues, resolved, prevIssues, rate);

        // 저장 (주간리포트 탭에서 목록으로 보임)
        weeklyReportRepository.save(WeeklyReport.of(
                userId, lastMonday, thisMonday.minusDays(1), issues, resolved, prevIssues, topCategory, summary));

        // 2) 메일 발송 (이메일 있을 때만)
        if (u.getEmail() != null) {
            String name = u.getNickname() != null ? u.getNickname() : "회원";

            // 전주 대비 한 줄 — 증감 %는 전주 값이 있을 때만(0으로 나누기 방지)
            String trend = prevIssues == 0 ? "지난주부터 집계를 시작했어요."
                    : issues < prevIssues ? "전주 " + prevIssues + "건 → " + pct(prevIssues, issues) + "% 감소"
                    : issues > prevIssues ? "전주 " + prevIssues + "건 → " + pct(prevIssues, issues) + "% 증가"
                    : "전주와 같아요.";

            // 카테고리별 발생 — 코랄 막대(발생 총합 대비 비율). 이메일 안전하게 table로 그림
            StringBuilder catRows = new StringBuilder();
            for (Object[] c : cats) {
                int cnt = ((Number) c[1]).intValue();
                int w = (int) Math.round(cnt * 100.0 / issues);
                catRows.append("""
                    <tr>
                      <td width="76" style="padding:5px 8px 5px 0;color:#1b2a4a;font-size:12px;font-weight:bold;white-space:nowrap;">%s</td>
                      <td style="padding:5px 0;">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border:2px solid #1b2a4a;">
                          <tr>
                            <td bgcolor="#f0704a" width="%d%%" style="height:13px;line-height:13px;font-size:0;">&nbsp;</td>
                            <td bgcolor="#eae6da" style="height:13px;line-height:13px;font-size:0;">&nbsp;</td>
                          </tr>
                        </table>
                      </td>
                      <td width="34" style="padding:5px 0 5px 8px;text-align:right;color:#1b2a4a;font-weight:bold;font-size:12px;white-space:nowrap;">%d건</td>
                    </tr>
                    """.formatted(catKo(String.valueOf(c[0])), w, cnt));
            }

            // 다음 주 추천 — 좌측 코랄 바가 붙은 박스 (앱 팝업과 동일 규칙)
            StringBuilder actions = new StringBuilder();
            if (topCategory != null) actions.append(actionBox(catKo(topCategory) + " 유형 학습카드를 복습해보세요."));
            if (resolved < issues) actions.append(actionBox("미해결 이슈를 스튜디오에서 마저 판정해보세요."));
            actions.append(actionBox("이번 주도 PR을 올려 리뷰를 받아보세요."));

            String inner = """
                <p style="margin:0 0 6px;color:#1b2a4a;font-size:18px;font-weight:bold;">%s님의 지난주 성장 리포트</p>
                <p style="margin:0 0 18px;color:#40507a;font-size:13px;line-height:1.6;">%s</p>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin:0 0 12px;">
                  <tr>
                    <td width="34%%" valign="top" style="padding-right:6px;">
                      <table width="100%%" cellpadding="0" cellspacing="0" style="border:3px solid #1b2a4a;background:#ffd23f;"><tr><td style="padding:14px 4px;text-align:center;">
                        <div style="font-size:24px;font-weight:bold;color:#1b2a4a;line-height:1;">%d</div>
                        <div style="font-size:11px;color:#1b2a4a;margin-top:5px;">발생 이슈</div>
                      </td></tr></table>
                    </td>
                    <td width="33%%" valign="top" style="padding:0 3px;">
                      <table width="100%%" cellpadding="0" cellspacing="0" style="border:3px solid #1b2a4a;background:#c8f2df;"><tr><td style="padding:14px 4px;text-align:center;">
                        <div style="font-size:24px;font-weight:bold;color:#1b7a52;line-height:1;">%d</div>
                        <div style="font-size:11px;color:#1b2a4a;margin-top:5px;">해결</div>
                      </td></tr></table>
                    </td>
                    <td width="33%%" valign="top" style="padding-left:6px;">
                      <table width="100%%" cellpadding="0" cellspacing="0" style="border:3px solid #1b2a4a;background:#fdfcf7;"><tr><td style="padding:14px 4px;text-align:center;">
                        <div style="font-size:24px;font-weight:bold;color:#1b2a4a;line-height:1;">%d%%</div>
                        <div style="font-size:11px;color:#1b2a4a;margin-top:5px;">해결률</div>
                      </td></tr></table>
                    </td>
                  </tr>
                </table>
                <p style="margin:0 0 24px;text-align:center;">
                  <span style="display:inline-block;background:#1b2a4a;color:#ffd23f;font-size:12px;font-weight:bold;padding:5px 14px;">%s</span>
                </p>

                <p style="margin:0 0 5px;color:#1b2a4a;font-size:13px;font-weight:bold;border-left:5px solid #f0704a;padding-left:8px;">최다 이슈 카테고리</p>
                <p style="margin:0 0 24px;padding-left:13px;color:#40507a;font-size:14px;font-weight:bold;">%s</p>

                <p style="margin:0 0 10px;color:#1b2a4a;font-size:13px;font-weight:bold;border-left:5px solid #f0704a;padding-left:8px;">카테고리별 발생</p>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin:0 0 24px;">%s</table>

                <p style="margin:0 0 10px;color:#1b2a4a;font-size:13px;font-weight:bold;border-left:5px solid #f0704a;padding-left:8px;">코기의 다음 주 추천</p>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin:0 0 6px;">%s</table>
                """.formatted(
                    name, summary,
                    issues, resolved, rate, trend,
                    topCategory == null ? "-" : catKo(topCategory),
                    catRows, actions);

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
    // 증감률(%) — |1 - 현재/이전| × 100. trend에서 prevIssues>0일 때만 호출됨
    private int pct(int prev, int cur) {
        return (int) Math.round(Math.abs((1 - cur / (double) prev)) * 100);
    }

    // 카테고리 코드 → 한글 (프론트 CATEGORY_KO와 동일)
    private String catKo(String code) {
        return switch (code) {
            case "BUG" -> "버그";
            case "PERFORMANCE" -> "성능";
            case "CODE_SMELL" -> "코드 냄새";
            case "CONVENTION" -> "컨벤션";
            case "SECURITY" -> "보안";
            default -> code;
        };
    }

    // 좌측 코랄 바 박스
    private String actionBox(String text) {
        return """
            <tr><td style="padding:4px 0;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="border:2px solid #1b2a4a;background:#ffffff;"><tr>
                <td width="6" bgcolor="#283f6f" style="font-size:0;">&nbsp;</td>
                <td style="padding:9px 12px;color:#40507a;font-size:12.5px;">%s</td>
              </tr></table>
            </td></tr>
            """.formatted(text);
    }
}