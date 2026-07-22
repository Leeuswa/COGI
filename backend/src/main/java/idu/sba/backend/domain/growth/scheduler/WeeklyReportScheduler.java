package idu.sba.backend.domain.growth.scheduler;

import idu.sba.backend.domain.growth.service.WeeklyReportSender;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WeeklyReportScheduler {

    private final UserRepository userRepository;
    private final WeeklyReportSender sender;

    // 매주 월요일 09:00 (KST) — 지난주 성장 리포트 저장 + 메일 발송
    @Scheduled(cron = "0 0 9 ? * MON", zone = "Asia/Seoul")
    public void sendWeeklyReports() {
        // ponytail: 유저 수 적어 findAll. 많아지면 페이징/배치로.
        for (User u : userRepository.findAll()) {
            try {
                sender.sendFor(u.getId());
            } catch (Exception e) {
                // 한 명 실패가 배치 전체를 막지 않게 개별 격리
            }
        }
    }
}
