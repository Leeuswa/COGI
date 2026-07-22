package idu.sba.backend.domain.growth;

import idu.sba.backend.domain.growth.repository.WeeklyReportRepository;
import idu.sba.backend.domain.growth.service.WeeklyReportSender;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.DayOfWeek;
import java.time.LocalDate;

// 지난주 주간 리포트를 '지금 즉시' 실제 발송하는 수동 테스트.
//   !! 실행하면 실제 메일이 나갑니다. 월요일 09시 크론을 안 기다리고 결과를 확인할 때만 사용.
//   중복 방지(existsByUserIdAndPeriodStart)를 우회하려고 지난주 리포트가 있으면 지우고 재생성한다.
//
// 실행 방법 — SEND_WEEKLY=true 환경변수가 있어야만 동작(없으면 자동 스킵 → 일반 빌드 안전):
//   · IntelliJ: 실행 구성 > Environment variables 에 SEND_WEEKLY=true 추가 후 실행
//   · CLI: SEND_WEEKLY=true ./gradlew test --tests "*WeeklyReportSendTest*"
@SpringBootTest
class WeeklyReportSendTest {

    @Autowired WeeklyReportSender sender;
    @Autowired WeeklyReportRepository reportRepository;
    @Autowired UserRepository userRepository;

    @Test
    void weeklyMailSender() {
        Assumptions.assumeTrue("true".equals(System.getenv("SEND_WEEKLY")),
                "SEND_WEEKLY=true 가 아니면 스킵 (실제 메일 발송 방지)");

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
