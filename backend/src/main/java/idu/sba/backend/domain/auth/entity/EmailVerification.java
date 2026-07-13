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

    // 저장 직전 발송 시각 자동 기록
    @PrePersist
    protected void onCreate(){
        this.createAt = LocalDateTime.now();
    }

    private EmailVerification(String email,String code, Purpose purpose, LocalDateTime expiresAt){
        this.email = email;
        this.code = code;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }

    public static EmailVerification issue(String email, String code, Purpose purpose, int ttlMinutes){
        return new EmailVerification(email,code, purpose
                ,LocalDateTime.now().plusMinutes(ttlMinutes)); //지금 + n분 = 만료시
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


}
