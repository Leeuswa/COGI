package idu.sba.backend.domain.terms.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId; //동의한 사용자
    private Long termId; //동의한 약관

    private String agreedVersion; // 동의 당시 약관 버전 — 이 값이 현재 약관 버전과 다르면 재동의 대상 (FR-91)

    private Boolean agreed = true; // 동의여부
    private String channelPreference;   // 마켓팅 수신 채널
    private LocalDateTime agreeAt; //동의 일시

    @PrePersist
    protected void onCreate(){
        this.agreeAt = LocalDateTime.now();
    }

    private UserAgreement(Long userId, Long termId, String agreedVersion){
        this.userId = userId;
        this.termId =termId;
        this.agreedVersion = agreedVersion;
    }

    public static UserAgreement of(Long userId, Long termId, String agreedVersion){
        return new UserAgreement(userId, termId, agreedVersion);
    }

    // 약관 개정 후 재동의 — 버전과 동의일시를 최신으로 갱신
    public void reagree(String currentVersion){
        this.agreedVersion = currentVersion;
        this.agreed = true;
        this.agreeAt = LocalDateTime.now();
    }

    // 선택 약관 동의 철회 — 행은 남기고 agreed 만 false (이력 보존)
    public void withdraw(){
        this.agreed = false;
        this.agreeAt = LocalDateTime.now();
    }


}
