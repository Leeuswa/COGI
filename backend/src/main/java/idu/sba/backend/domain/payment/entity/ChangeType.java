package idu.sba.backend.domain.payment.entity;

public enum ChangeType {
    UPGRADE,    // 플랜 전환
    RENEWAL,    // 정기결제 갱신 (매달 같은 플랜 재청구)
    CANCEL      // 해지/강등 (FREE로 내려감)
}
