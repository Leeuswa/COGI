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

    private Boolean agreed = true; // 동의여부
    private String channelPreference;// 마켓팅 수신 채널
    private LocalDateTime agreeAt; //동의 일시

    @PrePersist
    protected void onCreate(){
        this.agreeAt = LocalDateTime.now();
    }

    private UserAgreement(Long userId, Long termId){
        this.userId = userId;
        this.termId =termId;
    }

    public static UserAgreement of(Long userId, Long termId){
        return new UserAgreement(userId,termId);
    }


}
