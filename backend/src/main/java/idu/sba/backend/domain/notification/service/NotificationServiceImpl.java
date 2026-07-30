package idu.sba.backend.domain.notification.service;

import idu.sba.backend.domain.notification.dto.NotificationResponseDTO;
import idu.sba.backend.domain.notification.entity.Notification;
import idu.sba.backend.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponseDTO> getMyNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponseDTO::of)
                .toList();
    }

    @Override
    @Transactional
    public void dismiss(Long userId, Long notificationId) {
        // 본인 것만 삭제 — 남의 알림 id를 넣어도 조용히 무시(권한 노출 방지)
        notificationRepository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .ifPresent(notificationRepository::delete);
    }

    @Override
    @Transactional
    public void broadcast(List<Long> userIds, String icon, String title, String text, String link) {
        broadcast(userIds, icon, title, text, link, null);
    }

    @Override
    @Transactional
    public void broadcast(List<Long> userIds, String icon, String title, String text, String link, Long noticeId) {
        List<Notification> rows = userIds.stream()
                .map(uid -> Notification.ofNotice(uid, icon, title, text, link, noticeId))
                .toList();
        notificationRepository.saveAll(rows);
    }

    @Override
    @Transactional
    public void deleteByNoticeId(Long noticeId) {
        notificationRepository.deleteByNoticeId(noticeId);
    }

    @Override
    @Transactional
    public void markLegacyNoticeDeleted(String subject, String content) {
        notificationRepository.markLegacyNoticeDeleted(subject, content);
    }
}
