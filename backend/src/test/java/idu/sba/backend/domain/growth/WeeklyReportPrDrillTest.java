package idu.sba.backend.domain.growth;

import idu.sba.backend.domain.growth.dto.WeeklyReportPrDTO;
import idu.sba.backend.domain.growth.entity.WeeklyReport;
import idu.sba.backend.domain.growth.repository.WeeklyReportRepository;
import idu.sba.backend.domain.growth.service.WeeklyReportService;
import idu.sba.backend.domain.learning.repository.QuizSubmissionRepository;
import idu.sba.backend.domain.retention.repository.UserStreakRepository;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import idu.sba.backend.global.mail.HtmlMailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyReportPrDrillTest {

    @Mock private WeeklyReportRepository weeklyReportRepository;
    @Mock private ReviewIssueRepository reviewIssueRepository;
    @Mock private UserRepository userRepository;
    @Mock private HtmlMailSender htmlMailSender;
    @Mock private QuizSubmissionRepository quizSubmissionRepository;
    @Mock private UserStreakRepository userStreakRepository;

    @InjectMocks private WeeklyReportService service;

    private static final Long USER = 7L, REPORT = 100L;

    private WeeklyReport report(Long userId) {
        // periodStart(월)~periodEnd(일). 소유자 검증·window 계산에만 쓰인다
        return WeeklyReport.of(userId, LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26),
                8, 3, 5, "SECURITY", "요약");
    }

    // 네이티브 쿼리가 돌려주는 한 행: {prId, repoName, prNumber, title, issueCount, resolvedCount}
    // 카운트는 드라이버마다 Long/BigInteger라 섞어서 캐스팅까지 검증
    private Object[] row(long prId, String repo, int pr, String title, long issues, Object resolved) {
        return new Object[]{prId, repo, pr, title, issues, resolved};
    }

    @Test
    void 발생탭_OPEN은_모든_PR을_그대로_매핑한다() {
        when(weeklyReportRepository.findById(REPORT)).thenReturn(Optional.of(report(USER)));
        when(reviewIssueRepository.weeklyPrBreakdown(eq(USER), any(), any())).thenReturn(List.of(
                row(10L, "COGI", 69, "결제 수정", 5L, BigInteger.valueOf(2)),
                row(11L, "other", 70, "리팩터링", 3L, 0L)));

        List<WeeklyReportPrDTO> prs = service.getReportPrs(USER, REPORT, "OPEN");

        assertThat(prs).hasSize(2);
        WeeklyReportPrDTO first = prs.get(0);
        assertThat(first.prId()).isEqualTo(10L);
        assertThat(first.repoName()).isEqualTo("COGI");
        assertThat(first.prNumber()).isEqualTo(69);
        assertThat(first.issueCount()).isEqualTo(5);
        assertThat(first.resolvedCount()).isEqualTo(2);   // BigInteger → int 캐스팅 확인
    }

    @Test
    void 해결탭_RESOLVED는_해결건_있는_PR만_남긴다() {
        when(weeklyReportRepository.findById(REPORT)).thenReturn(Optional.of(report(USER)));
        when(reviewIssueRepository.weeklyPrBreakdown(eq(USER), any(), any())).thenReturn(List.of(
                row(10L, "COGI", 69, "결제 수정", 5L, 2L),   // 해결 2건 → 남음
                row(11L, "other", 70, "리팩터링", 3L, 0L)));  // 해결 0건 → 제외

        List<WeeklyReportPrDTO> prs = service.getReportPrs(USER, REPORT, "RESOLVED");

        assertThat(prs).hasSize(1);
        assertThat(prs.get(0).prId()).isEqualTo(10L);
        assertThat(prs.get(0).resolvedCount()).isEqualTo(2);
    }

    @Test
    void 남의_리포트는_차단된다() {
        when(weeklyReportRepository.findById(REPORT)).thenReturn(Optional.of(report(999L))); // 다른 사람 소유

        assertThatThrownBy(() -> service.getReportPrs(USER, REPORT, "OPEN"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
