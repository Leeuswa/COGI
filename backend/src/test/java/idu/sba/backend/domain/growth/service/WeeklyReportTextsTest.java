package idu.sba.backend.domain.growth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyReportTextsTest {

    @Test
    void 비율은_반올림하고_분모가_0이면_0() {
        assertThat(WeeklyReportTexts.percent(2, 3)).isEqualTo(67);
        assertThat(WeeklyReportTexts.percent(0, 0)).isZero();
    }

    @Test
    void 전주_대비_증감률() {
        assertThat(WeeklyReportTexts.changePct(10, 7)).isEqualTo(30);  // 감소
        assertThat(WeeklyReportTexts.changePct(4, 6)).isEqualTo(50);   // 증가
        assertThat(WeeklyReportTexts.changePct(0, 5)).isZero();        // 전주 없음
    }

    @Test
    void 추천_문구는_카테고리를_한글로() {
        assertThat(WeeklyReportTexts.nextActions("BUG", 5, 3))
                .containsExactly("버그 유형 학습카드를 복습해보세요.",
                        "미해결 이슈를 스튜디오에서 마저 판정해보세요.",
                        "이번 주도 PR을 올려 리뷰를 받아보세요.");
    }
}
