package idu.sba.backend.domain.review.entity;

public enum ReviewTargetType {

    PR,     //GitHub PR 웹훅 경로(REV-002/003) — 아직 미구현, 항상 prId 채워짐
    GUEST,  //비로그인 체험(domain/guest, Redis 저장) — 이 테이블에는 안 씀
    PASTE,  //로그인 사용자 코드 붙여넣기(API-040)
    UPLOAD  //로그인 사용자 파일 업로드(API-041)

}
