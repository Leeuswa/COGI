package idu.sba.backend.domain.growth.service;

import idu.sba.backend.domain.growth.entity.WeeklyReport;

import java.util.List;

// 주간 리포트 메일 본문 — 월요일 자동 발송(WeeklyReportSender)과 재발송(WeeklyReportService.sendMail)이 같은 템플릿을 쓴다
final class WeeklyReportMail {

    private WeeklyReportMail() {}

    // cats: categoryBreakdown 결과 [카테고리, 건수] (건수 많은 순)
    static String render(String heading, WeeklyReport rp, List<Object[]> cats) {
        int issues = rp.getIssueCount();
        int resolved = rp.getResolvedCount();
        int prevIssues = rp.getPrevIssueCount();
        String topCategory = rp.getTopCategory();
        int rate = WeeklyReportTexts.percent(resolved, issues);

        // 전주 대비 한 줄 — 증감 %는 전주 값이 있을 때만(0으로 나누기 방지)
        String trend = prevIssues == 0 ? "지난주부터 집계를 시작했어요."
                : issues < prevIssues ? "전주 " + prevIssues + "건 → " + WeeklyReportTexts.changePct(prevIssues, issues) + "% 감소"
                : issues > prevIssues ? "전주 " + prevIssues + "건 → " + WeeklyReportTexts.changePct(prevIssues, issues) + "% 증가"
                : "전주와 같아요.";

        // 카테고리별 발생 — 코랄 막대(발생 총합 대비 비율). 이메일 안전하게 table로 그림
        StringBuilder catRows = new StringBuilder();
        for (Object[] c : cats) {
            int cnt = ((Number) c[1]).intValue();
            int w = WeeklyReportTexts.percent(cnt, issues);
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
                """.formatted(WeeklyReportTexts.catKo(String.valueOf(c[0])), w, cnt));
        }

        // 다음 주 추천 — 좌측 코랄 바가 붙은 박스 (앱 팝업과 동일 규칙)
        StringBuilder actions = new StringBuilder();
        for (String a : WeeklyReportTexts.nextActions(topCategory, issues, resolved)) actions.append(actionBox(a));

        return """
            <p style="margin:0 0 6px;color:#1b2a4a;font-size:18px;font-weight:bold;">%s</p>
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
                heading, rp.getSummary(),
                issues, resolved, rate, trend,
                topCategory == null ? "-" : WeeklyReportTexts.catKo(topCategory),
                catRows, actions);
    }

    // 좌측 코랄 바 박스
    private static String actionBox(String text) {
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
