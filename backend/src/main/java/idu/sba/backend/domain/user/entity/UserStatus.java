package idu.sba.backend.domain.user.entity;

public enum UserStatus {
    ACTIVE, // 계정 활성중
    SUSPENDED, // 계정 정지
    WITHDRAWN // 회원탈퇴 — 소프트삭제(개인정보 익명화, 이력은 보존)
}
