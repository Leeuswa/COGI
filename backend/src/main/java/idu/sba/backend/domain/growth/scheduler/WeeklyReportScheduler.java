package idu.sba.backend.domain.growth.scheduler;

import idu.sba.backend.domain.growth.service.WeeklyReportSender;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.entity.UserStatus;
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
        // 탈퇴·정지 회원은 제외 (탈퇴 회원은 이메일이 익명화돼 있어 메일이 엉뚱한 주소로 나감)
        for (User u : userRepository.findByStatus(UserStatus.ACTIVE)) {
            try {
                sender.sendFor(u.getId());
            } catch (Exception e) {
                // 한 명 실패가 배치 전체를 막지 않게 개별 격리
            }
        }
    }
}
