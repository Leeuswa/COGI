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


    //   LOCAL: 가입 이메일
    //   GITHUB/KAKAO: 소셜 계정 이메일(없으면 null 가능)
    @Column(unique = true)
    private String email;

    private String password;

    private String nickname;

    @Column(unique = true)
    private String githubId; //GitHub OAuth 식별자

    private String githubUsername;

    @Column(length = 500)
    private String githubAccessToken;

    @Enumerated(EnumType.STRING)
    private Provider provider =Provider.LOCAL; // 가입 방식(기본 Local 설정)

    @Column(unique = true)
    private String kakaoId; //카카오 고유 ID

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
    public User(String email, String password,String nickname){
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }

    //github 최초 로그인 시
    public static User createByGithub(String githubId, String githubUsername,
                                      String email, String githubAccessToken){

            User user = new User();
            user.provider = Provider.GITHUB;
            user.githubId = githubId;
            user.githubUsername = githubUsername;
            user.nickname = githubUsername;
            user.email =email;
            user.githubAccessToken = githubAccessToken;
            user.status = UserStatus.ACTIVE;
            return user;
    }

    public static User createByKakao(String kakaoId,String nickname ,String email){
        User user = new User();
        user.provider = Provider.KAKAO;
        user.kakaoId = kakaoId;
        user.nickname = nickname;
        user.email = email;
        user.status = UserStatus.ACTIVE;
        return user;
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


    //비밀번호 재설정: 새 비밀번호 변경 및 계정 잠금 해제
    public void resetPassword(String encodedPassword){
        this.password = encodedPassword; // 암호화된 비밀번호로 받음
        this.loginFailCount = 0; //실패 횟수 초기화
        this.isLocked = false; //계정 잠금 해제
    }

    // 구독 플랜 변경: 캐시용 plan_id 갱신
    public void updatePlanId(Long planId) {
        this.planId = planId;
    }

    // 관리자 회원관리 계정 상태(ACTIVE/SUSPENDED) 즉시 변경
    public void changeStatus(UserStatus status) {
        this.status = status;
    }

    // 관리자 회원관리 권한(USER/ADMIN) 즉시 변경
    public void changeRole(Role role) {
        this.role = role;
    }


    //GitHub 재로그인 시 토큰 갱신(스코프 재동의로 토큰이 바뀔 수 있음)
    public void updateGithubAccessToken(String githubAccessToken){
        this.githubAccessToken = githubAccessToken;
    }


    // 프로필 수정 (관심기술/수준)
    public void updateProfile(String nickname,Level level, String interests) {
        this.nickname = nickname;
        this.level = level;
        this.interests = interests;
    }

    // 온보딩 제출
    public void completeOnboarding(Level level, String interests, boolean guideConfirmed) {
        this.level = level;
        this.interests = interests;
        this.guideConfirmed = guideConfirmed;
        this.onboardingCompleted = true;   // 온보딩 완료 표시
    }

    // 일반회원,카카오 사용자 계정에 깃허브 연동
    public void linkGithub(String githubId,String githubUsername,String githubAccessToken){
        this.githubId = githubId;
        this.githubUsername = githubUsername;
        this.githubAccessToken = githubAccessToken;
    }


    //로그인하고 마이페이지에서 비밀번호 변경
    public void changePassword(String encodedPassword){
        this.password = encodedPassword;
    }

    //2단계 인증
    public void prepareTotp(String secret) {
        this.totpSecret = secret; //시크릿 저장
        this.totpEnabled = false;
    }

    public void enableTotp() {
        this.totpEnabled = true; // 로그인시 2차 인증 요구 대상
    }



}
