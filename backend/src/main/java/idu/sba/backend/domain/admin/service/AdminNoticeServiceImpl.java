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
    public SendNoticeResponseDTO sendNoticeToAll(Long senderId, String subject, String content, boolean urgent) {
        // 활성 회원 대상
        List<User> activeUsers = userRepository.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .toList();

        List<Long> userIds = activeUsers.stream().map(User::getId).toList();

        // 이메일은 긴급공지일 때만, 그것도 주소 있는 대상만. 일반 공지는 인앱 알림만.
        List<String> emails = urgent
                ? activeUsers.stream().map(User::getEmail).filter(e -> e != null && !e.isBlank()).toList()
                : List.of();

        // 대상 수: 긴급=이메일 대상, 일반=인앱 알림 대상
        int recipients = urgent ? emails.size() : userIds.size();
        Notice notice = noticeRepository.save(
                Notice.create(senderId, subject, content, recipients, urgent)); // 즉시 커밋

        // 인앱 알림(종 아이콘)은 항상 즉시 생성 — noticeId로 공지와 엮어 삭제 때 함께 지운다
        notificationService.broadcast(userIds, urgent ? "🚨" : "📢", subject, content, "/app", notice.getId());

        if (urgent) {
            dispatcher.dispatch(notice.getId(), subject, renderBody(subject, content), emails); // @Async → markSent
        } else {
            notice.markSent(userIds.size(), 0); // 알림만: 메일 발송이 없어 즉시 완료 처리(안 그러면 '발송 중'에 멈춤)
            noticeRepository.save(notice);
        }
        return new SendNoticeResponseDTO(notice.getId(), recipients);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoticeResponseDTO> getNotices() {
        return noticeRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(NoticeResponseDTO::of)
                .toList();
    }

    // 이력 row + 인앱 알림 정리. 새 공지는 noticeId로 알림을 완전 삭제하고,
    // 링크 없는 예전 공지는 제목/내용으로 찾아 '삭제된 알림입니다'로 표시한다.
    // 이미 나간 이메일은 되돌릴 수 없다.
    @Override
    @Transactional
    public void deleteNotice(Long noticeId) {
        Notice notice = noticeRepository.findById(noticeId).orElse(null);
        notificationService.deleteByNoticeId(noticeId);        // 새 공지 알림: 완전 삭제
        if (notice != null) {                                  // 예전 공지 알림: 링크가 없어 문구로 매칭해 표시만 바꿈
            notificationService.markLegacyNoticeDeleted(notice.getSubject(), notice.getContent());
        }
        noticeRepository.deleteById(noticeId);
    }

    // 관리자 입력은 평문 → HTML 이스케이프 후 줄바꿈만 <br>로 살린다 (본문 주입 방지).
    // 이메일은 긴급공지에만 나가므로 항상 '긴급공지' 배지를 붙인다(마이페이지 팝업과 통일된 COGI 디자인).
    private String renderBody(String subject, String content) {
        return """
            <span style="display:inline-block;background:#ffd23f;color:#1b2a4a;border:2px solid #1b2a4a;\
            font-size:11px;font-weight:bold;letter-spacing:1px;padding:2px 9px;margin-bottom:12px;">🚨 긴급공지</span>
            <h2 style="margin:0 0 16px;color:#1b2a4a;font-size:19px;line-height:1.35;">%s</h2>
            <div style="color:#40507a;font-size:11px;font-weight:bold;margin-bottom:8px;">내용</div>
            <div style="border:2px solid #1b2a4a;background:#ffffff;padding:14px 16px;box-shadow:3px 3px 0 #1b2a4a;\
            color:#1b2a4a;font-size:14px;line-height:1.8;">%s</div>
            """.formatted(escape(subject), escape(content).replace("\n", "<br>"));
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
