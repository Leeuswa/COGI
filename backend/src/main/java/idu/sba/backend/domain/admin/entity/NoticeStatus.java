package idu.sba.backend.domain.admin.entity;

public enum NoticeStatus {
    SENDING, // 비동기 발송 진행 중
    SENT     // 발송 완료(성공/실패 건수 확정)
}
