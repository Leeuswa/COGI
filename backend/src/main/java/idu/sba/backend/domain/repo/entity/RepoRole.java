package idu.sba.backend.domain.repo.entity;

public enum RepoRole {

    OWNER,  //레포 등록자(팀장) — 이슈 최종 승인 권한(FR-37)
    MEMBER  //초대받아 합류한 팀원 — 이슈 해결 요청 권한(FR-37)

}
