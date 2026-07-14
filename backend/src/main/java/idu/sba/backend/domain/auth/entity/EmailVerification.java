package idu.sba.backend.domain.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email; //인증 대상 이메일
    private String code; // 발송한 인증 코드

    @Enumerated(EnumType.STRING)
    private Purpose purpose; // SIGNUP / FIND_ID / RESET_PW

    private LocalDateTime expiresAt; //만료 시각 (발송 + 5분)
    private Boolean verified = false; //인증 완료 여부 (검증 성공 시 true)
    private LocalDateTime createAt; // 발송시각


    private Integer attemptCount = 0;      // 현재 단계에서 틀린 횟수
    private LocalDateTime lockedUntil;      // 이 시각까지 정지 (null=정지 아님)
    private Integer lockStage = 0;          // 0=정지없음, 1=30분정지 겪음

    private static final int FAIL_LIMIT = 3;   // 3회 틀리면 정지

    // 저장 직전 발송 시각 자동 기록
    @PrePersist
    protected void onCreate(){
        this.createAt = LocalDateTime.now();
    }

    private EmailVerification(String email,String code, Purpose purpose, LocalDateTime expiresAt,int lockStage){
        this.email = email;
        this.code = code;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
        this.lockStage = lockStage;
    }

    public static EmailVerification issue(String email, String code, Purpose purpose, int ttlMinutes, int lockStage){
        return new EmailVerification(email,code, purpose
                ,LocalDateTime.now().plusMinutes(ttlMinutes),lockStage); //지금 + n분 = 만료시
    }

    //만료시각 검증
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    //입력 코드가 저장된 코드와 같은지 확인
    public boolean matches(String input){
        return this.code.equals(input);
    }

    //인증 완료 처리
    public void markVerified(){
        this.verified = true;
    }


    //정지 상태 확인
    public boolean isLocked(){
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    // 검증 실패 기록 (+1, 3회 도달 시 단계별 정지: 1차 30분 / 2차 하루)
    public void recordFail() {
        this.attemptCount++;
        if (this.attemptCount >= FAIL_LIMIT) {
            LocalDateTime now = LocalDateTime.now();
            if (this.lockStage == 0) {
                this.lockedUntil = now.plusMinutes(30);
                this.lockStage = 1;
            } else {
                this.lockedUntil = now.plusHours(24);
            }
            this.attemptCount = 0;
        }
    }

}
