package idu.sba.backend.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
//아무나 빈 객체 만들지 못하게 protected로 막음
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;   //GitHub 전용 가입자는 null 일 수 있음

    private String password;

    @Column(unique = true)
    private String githubId; //GitHub OAuth 식별자

    private String githubUsername;

    @Column(length = 500)
    private String githubAccessToken;

    private String totpSecret; //2단계 인증 시크릿


    private Boolean totpEnabled = false;

    @Enumerated(EnumType.STRING)
    private Level level; //가입시 null

    @Column(length = 500)
    private String interests; // 관심기술


    private Boolean onboardingCompleted = false;


    private Boolean guideConfirmed = false;


    private Integer loginFailCount = 0;   // 로그인 5회 실패 시 잠금


    private Boolean isLocked = false;

    private Long planId;            // 구독 플랜 캐시, null=FREE 기본


    @Enumerated(EnumType.STRING) //enum을 String으로 저장
    private Role role = Role.USER;


    @Enumerated(EnumType.STRING)
    private UserStatus status =  UserStatus.ACTIVE; // ACTIVE/SUSPENDED

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist //  insert 되기 직전에 자동 호출되는 JPA 콜백
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate                // UPDATE 되기 직전에 자동 호출되는 JPA 콜백
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public User(String email, String password){
        this.email = email;
        this.password = password;
    }

    //로그인 실패: 실패횟수 + 1, 5회 이상이면 잠금
    public void increaseLoginFailCount(){
        this.loginFailCount++;
        if(this.loginFailCount >= 5){
            this.isLocked = true;
        }
    }

    //로그인 성공:실패횟수 초기화
    public void resetLoginFailCount(){
        this.loginFailCount = 0;
    }


}
