package idu.sba.backend.domain.growth.service;

import idu.sba.backend.domain.growth.entity.WeeklyReport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyReportMailTest {

    @Test
    void 수치와_카테고리가_본문에_들어간다() {
        WeeklyReport rp = WeeklyReport.of(1L, LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26),
                4, 3, 8, "BUG", "지난주 발생 4건 중 3건 해결(해결률 75%).");
        List<Object[]> cats = List.of(new Object[]{"BUG", 3L}, new Object[]{"SECURITY", 1L});

        String html = WeeklyReportMail.render("코기님의 지난주 성장 리포트", rp, cats);

        assertThat(html)
                .contains("코기님의 지난주 성장 리포트")
                .contains("75%")                       // 해결률
                .contains("전주 8건 → 50% 감소")
                .contains("버그").contains("보안")      // 카테고리 한글
                .contains("미해결 이슈를 스튜디오에서 마저 판정해보세요.");
    }
}
