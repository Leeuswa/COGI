package idu.sba.backend.domain.admin.dto;

// 공지 발송 시작 결과 — 실제 발송은 비동기라 대상 수만 즉시 반환(성공/실패 건수는 이력 목록에서 확인)
public record SendNoticeResponseDTO(Long noticeId, int recipientCount) {}
