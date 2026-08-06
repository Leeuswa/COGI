package idu.sba.backend.domain.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email; //대상 이메일
    private String resetToken; // 비밀번호 재설정 토큰
    private LocalDateTime expireAt; //만료 시각
    private Boolean used = false; //사용 여부 (한 번 쓰면 true -> 재사용 방지)
    private LocalDateTime createAt; // 생성 여부

    @PrePersist
    protected void onCreate(){
        this.createAt = LocalDateTime.now();
    }

    private PasswordResetToken(String email, String resetToken,LocalDateTime expireAt){
        this.email = email;
        this.resetToken = resetToken;
        this.expireAt = expireAt;
    }

    public static PasswordResetToken issue(String email,String resetToken,int ttlMinutes){
        return new PasswordResetToken(email,resetToken,
                LocalDateTime.now().plusMinutes(ttlMinutes));
    }

    //만료 됐는지
    public boolean isExpired(){
        return expireAt.isBefore(LocalDateTime.now());
    }

    //사용 여부 완료 처리
    public void markUsed(){
        this.used = true;
    }


}
