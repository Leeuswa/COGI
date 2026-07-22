package idu.sba.backend.domain.admin.dto;

import idu.sba.backend.domain.admin.entity.Notice;
import idu.sba.backend.domain.admin.entity.NoticeStatus;

import java.time.LocalDateTime;

// 관리자 공지 이력 한 행 (목록 표시용)
public record NoticeResponseDTO(
        Long id,
        String subject,
        String content,
        int recipientCount,
        Integer successCount, // 발송 중이면 null
        Integer failCount,
        NoticeStatus status,  // "SENDING"/"SENT"
        LocalDateTime createdAt
) {
    public static NoticeResponseDTO of(Notice n) {
        return new NoticeResponseDTO(n.getId(), n.getSubject(), n.getContent(), n.getRecipientCount(),
                n.getSuccessCount(), n.getFailCount(), n.getStatus(), n.getCreatedAt());
    }
}
