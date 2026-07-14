package idu.sba.backend.domain.terms.entity;

public enum TermType {
    SERVICE, //이용약관 동의(필수)
    PRIVACY, //개인정처리방침 (필수)
    PAYMENT, //결제이용약관(결제시 필수, 회원가입 때는 false)
    MARKETING //마켓수신동 (선택)
}
