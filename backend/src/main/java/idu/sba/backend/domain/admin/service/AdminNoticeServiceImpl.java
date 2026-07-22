package idu.sba.backend.domain.admin.service;

import idu.sba.backend.domain.admin.dto.NoticeResponseDTO;
import idu.sba.backend.domain.admin.dto.SendNoticeResponseDTO;
import idu.sba.backend.domain.admin.entity.Notice;
import idu.sba.backend.domain.admin.repository.NoticeRepository;
import idu.sba.backend.domain.notification.service.NotificationService;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.entity.UserStatus;
import idu.sba.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminNoticeServiceImpl implements AdminNoticeService {

    private final UserRepository userRepository;
    private final NoticeRepository noticeRepository;
    private final NoticeMailDispatcher dispatcher;
    private final NotificationService notificationService;

    // 이 메서드가 트랜잭션으로 묶여 있으면 아직 커밋 전이라 그 row를 못 찾는 레이스가 생긴다(webhook과 동일 이유).
    @Override
    public SendNoticeResponseDTO sendNoticeToAll(Long senderId, String subject, String content) {
        // 활성 회원 대상
        List<User> activeUsers = userRepository.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .toList();

        // 인앱 알림(종 아이콘)은 즉시 생성 — 이메일 발송과 별개로 바로 뜬다
        List<Long> userIds = activeUsers.stream().map(User::getId).toList();
        notificationService.broadcast(userIds, "📢", subject, content, "/app");

        // 이메일은 주소 있는 대상만
        List<String> emails = activeUsers.stream()
                .map(User::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .toList();

        Notice notice = noticeRepository.save(
                Notice.create(senderId, subject, content, emails.size())); // 즉시 커밋

        dispatcher.dispatch(notice.getId(), subject, renderBody(subject, content), emails); // @Async
        return new SendNoticeResponseDTO(notice.getId(), emails.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoticeResponseDTO> getNotices() {
        return noticeRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(NoticeResponseDTO::of)
                .toList();
    }

    // 관리자 입력은 평문 → HTML 이스케이프 후 줄바꿈만 <br>로 살린다 (본문 주입 방지)
    private String renderBody(String subject, String content) {
        return "<h2 style=\"margin:0 0 14px;color:#1b2a4a;font-size:18px;\">" + escape(subject) + "</h2>"
                + "<div style=\"color:#1b2a4a;font-size:14px;line-height:1.7;\">" + escape(content).replace("\n", "<br>") + "</div>";
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
