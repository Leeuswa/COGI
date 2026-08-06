package idu.sba.backend.domain.admin.service;

import idu.sba.backend.domain.admin.entity.Notice;
import idu.sba.backend.domain.admin.repository.NoticeRepository;
import idu.sba.backend.global.mail.HtmlMailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

// 전체 공지 메일을 백그라운드에서 발송하고 결과를 Notice에 기록한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeMailDispatcher {

    private final NoticeRepository noticeRepository;
    private final HtmlMailSender htmlMailSender;

    @Async("mailExecutor")
    public void dispatch(Long noticeId, String subject, String innerHtml, List<String> emails) {
        int success = 0, fail = 0;
        for (String email : emails) {
            try {
                htmlMailSender.send(email, "[COGI 공지] " + subject, innerHtml);
                success++;
            } catch (Exception e) {
                fail++;
                log.warn("공지 발송 실패: {} - {}", email, e.getMessage());
            }
        }
        Notice notice = noticeRepository.findById(noticeId).orElse(null);
        if (notice == null) {
            log.warn("발송 결과 기록 실패 - notice가 없음 id={}", noticeId);
            return;
        }
        notice.markSent(success, fail);
        noticeRepository.save(notice);
        log.info("공지 발송 완료 - id={}, 성공={}, 실패={}", noticeId, success, fail);
    }
}
