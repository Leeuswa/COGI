package idu.sba.backend.domain.notification.service;

import idu.sba.backend.domain.notification.dto.NotificationResponseDTO;
import idu.sba.backend.domain.notification.entity.Notification;
import idu.sba.backend.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    public boolean createOncePerDay(Long userId, String icon, String title, String text, String link) {
        // 자정 기준. 같은 날 같은 link면 이미 알린 것으로 본다 — 다른 종류 알림이 쌓여 있어도 판정에 안 걸린다
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        if (notificationRepository.existsByUserIdAndLinkAndCreatedAtAfter(userId, link, startOfDay)) {
            return false;
        }
        notificationRepository.save(Notification.of(userId, icon, title, text, link));
        return true;
    }

    @Override
    @Transactional
    public void broadcast(List<Long> userIds, String icon, String title, String text, String link) {
        List<Notification> rows = userIds.stream()
                .map(uid -> Notification.of(uid, icon, title, text, link))
                .toList();
        notificationRepository.saveAll(rows);
    }
}
