package idu.sba.backend.domain.growth;

import idu.sba.backend.domain.growth.repository.WeeklyReportRepository;
import idu.sba.backend.domain.growth.service.WeeklyReportSender;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Properties;

// 지난주 주간 리포트를 '지금 즉시' 실제 발송하는 수동 테스트.
//   !! 실행하면 실제 메일이 나갑니다. 월요일 09시 크론을 안 기다리고 결과를 확인할 때만 사용.
//   중복 방지(existsByUserIdAndPeriodStart)를 우회하려고 지난주 리포트가 있으면 지우고 재생성한다.
//
// 실행 방법 — 아래 SEND_NOW 상수를 true로 두고 이 테스트를 실행하면 즉시 발송된다.
//   테스트 끝나면 SEND_NOW=false로 되돌려야 전체 빌드(./gradlew test)에서 메일이 안 나간다.
@SpringBootTest
class WeeklyReportSendTest {

    @Autowired WeeklyReportSender sender;
    @Autowired WeeklyReportRepository reportRepository;
    @Autowired UserRepository userRepository;

    // application.properties가 요구하는 ${...} env들을, "없을 때만" 더미로 채워 컨텍스트가 뜨게 한다.
    // 실제 env가 있으면 그대로 사용 → 메일도 진짜 계정(MAIL_USERNAME/PASSWORD)이 있으면 실제 발송된다.
    // 덕분에 env 없이 `./gradlew test`를 돌려도 이 테스트는 깨지지 않고 스킵된다(assumeTrue).
    @DynamicPropertySource
    static void fillMissingEnv(DynamicPropertyRegistry registry) {
        // 실제 메일 계정은 gitignore된 mail-local.properties 에서 읽는다(있으면 실제 발송, 없으면 더미).
        Properties mail = new Properties();
        try (var in = WeeklyReportSendTest.class.getClassLoader().getResourceAsStream("mail-local.properties")) {
            if (in != null) mail.load(in);
        } catch (Exception ignored) { /* 파일 없거나 못 읽으면 더미로 */ }
        String mailUser = blankTo(mail.getProperty("MAIL_USERNAME"), "test@example.com");
        String mailPass = blankTo(mail.getProperty("MAIL_PASSWORD"), "test");

        String[][] fallback = {
                {"CLAUDE_API_KEY", "test"}, {"GEMINI_API_KEY", "test"}, {"GROQ_API_KEY", "test"}, {"OPENAI_API_KEY", "test"},
                {"GITHUB_CLIENT_ID", "test"}, {"GITHUB_CLIENT_SECRET", "test"},
                {"GITHUB_LINK_CLIENT_ID", "test"}, {"GITHUB_LINK_CLIENT_SECRET", "test"}, {"GITHUB_WEBHOOK_SECRET", "test"},
                {"KAKAO_CLIENT_ID", "test"}, {"KAKAO_CLIENT_SECRET", "test"},
                {"TOSS_SECRET_KEY", "test"},
                {"JWT_SECRET", "test-jwt-secret-for-context-load-only-min-32-bytes-0123456789"},
                {"MAIL_USERNAME", mailUser}, {"MAIL_PASSWORD", mailPass},
        };
        for (String[] kv : fallback) {
            if (System.getenv(kv[0]) == null) registry.add(kv[0], () -> kv[1]); // 실제 env가 있으면 그게 우선
        }
    }

    private static String blankTo(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    // 실제 발송 스위치 — 테스트할 때만 true. 커밋 전 false로 되돌리기(전체 빌드에서 메일 안 나가게).
    private static final boolean SEND_NOW = true;

    @Test
    void weeklyMailSender() {
        Assumptions.assumeTrue(SEND_NOW, "SEND_NOW=false → 스킵 (실제 메일 발송 방지)");

        LocalDate lastMonday = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(1);

        for (User u : userRepository.findAll()) {
            // 중복 방지 우회: 지난주 리포트가 이미 있으면 지우고 재생성
            reportRepository.findByUserIdOrderByPeriodStartDesc(u.getId()).stream()
                    .filter(r -> lastMonday.equals(r.getPeriodStart()))
                    .forEach(reportRepository::delete);
            sender.sendFor(u.getId()); // 지난주 활동(issues>0) 있는 사용자에게만 실제 발송
        }
    }
}
