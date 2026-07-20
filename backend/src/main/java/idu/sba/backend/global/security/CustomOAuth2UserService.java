package idu.sba.backend.global.security;

import idu.sba.backend.domain.repo.service.RepoMemberService;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final RepoMemberService repoMemberService; //레포 초대 자동 매칭용(담당: 홍성찬)

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        //소셜 로그인 시도한 유저 정보를 받음 (JSON으로 받음)
        OAuth2User oAuth2User = super.loadUser(userRequest);

        //어떤 소셜 로그인 사용자인지(깃허브/카카오)
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        //JSON으로 받은 데이터를 Map으로 변환
        Map<String,Object> attributes = oAuth2User.getAttributes();

        User user;

        if ("github".equals(registrationId)) {
            String githubId = String.valueOf(attributes.get("id"));
            String username = (String) attributes.get("login");
            String email = (String) attributes.get("email");   // 비공개면 null 가능
            String accessToken = userRequest.getAccessToken().getTokenValue();

            if(email == null){
                email = fetchGithubPrimaryEmail(accessToken);
            }

            Optional<User> existingUser = userRepository.findByGithubId(githubId);
            if (existingUser.isPresent()) {
                user = existingUser.get();
                //스코프 재동의 등으로 토큰이 바뀔 수 있어 재로그인 시마다 갱신(레포 연동 API가 최신 토큰을 써야 함)
                user.updateGithubAccessToken(accessToken);
                userRepository.save(user);
            } else {
                //같은 이메일 있는지 확인
                if (email != null && userRepository.existsByEmail(email)) {
                    throw new OAuth2AuthenticationException(new OAuth2Error("email_exists"), "이미 이 이메일로 가입된 계정이 있습니다.");
                }
                user = userRepository.save(User.createByGithub(githubId, username, email, accessToken));
                //레포 초대 자동 매칭: 이 GitHub 계정/이메일로 대기 중인 초대가 있으면 자동 수락
                repoMemberService.autoMatchPendingInvitations(user);
            }
        } else { // kakao
            String kakaoId = String.valueOf(attributes.get("id"));
            // 카카오는 이메일이 중첩됨: kakao_account.email
            Map<String, Object> account = (Map<String, Object>) attributes.get("kakao_account");
            String email = (account != null) ? (String) account.get("email") : null;

            //닉네임 가져오기
            Map<String, Object> profile = (account != null) ? (Map<String, Object>) account.get("profile") : null;
            String nickname = (profile != null) ? (String) profile.get("nickname") : null;
            user = userRepository.findByKakaoId(kakaoId).orElse(null);
            if (user == null) {
                //같은 이메일 있는지 확인
                if (email != null && userRepository.existsByEmail(email)) {
                    throw new OAuth2AuthenticationException(new OAuth2Error("email_exists"), "이미 이 이메일로 가입된 계정이 있습니다.");
                }
                user = userRepository.save(User.createByKakao(kakaoId, nickname, email));
            }
        }

        //성공 핸들러에서 쓸 수 있게 userId/role 담아 반환
        Map<String, Object> customAttrs = new HashMap<>(attributes);
        customAttrs.put("userId", user.getId());
        customAttrs.put("role", user.getRole().name());

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                customAttrs,
                "userId");   // 이 attributes에서 이름(식별자)으로 쓸 키

    }
    // GitHub /user/emails 로 primary·verified 이메일 조회 (프로필 이메일이 비공개일 때)
    private String fetchGithubPrimaryEmail(String accessToken) {
        try {
            List<Map<String, Object>> emails = org.springframework.web.client.RestClient.create()
                    .get()
                    .uri("https://api.github.com/user/emails")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {});
            if (emails == null) return null;
            return emails.stream()
                    .filter(e -> Boolean.TRUE.equals(e.get("primary"))
                            && Boolean.TRUE.equals(e.get("verified")))
                    .map(e -> (String) e.get("email"))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;   // 조회 실패해도 로그인은 진행(email 없이)
        }
    }
}
