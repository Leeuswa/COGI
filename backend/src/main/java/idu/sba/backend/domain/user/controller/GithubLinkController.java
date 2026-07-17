package idu.sba.backend.domain.user.controller;

import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import idu.sba.backend.global.security.JwtProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me/github")
@RequiredArgsConstructor
public class GithubLinkController {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Value("${github.link.client-id}")     private String clientId;
    @Value("${github.link.redirect-uri}")  private String redirectUri;

    @Value("${github.link.client-secret}") private String clientSecret;
    @Value("${app.frontend-url}")          private String frontendUrl;


    //깃허 동의 화면으로 이동
    @GetMapping("/authorize")
    public void authorize(@AuthenticationPrincipal Long userId, HttpServletResponse response) throws IOException {

        //어떤 사용자가 연동 하는지
        String state = jwtProvider.createGithubLinkState(userId);

        String url = UriComponentsBuilder
                .fromUriString("https://github.com/login/oauth/authorize")
                .queryParam("client_id", clientId)         // 우리 연동앱 식별자
                .queryParam("redirect_uri", redirectUri)   // 동의 후 돌아올 우리 콜백
                .queryParam("scope", "read:user user:email")  // 요청 권한(프로필+이메일)
                .queryParam("state", state)                // CSRF 방어 + userId 전달
                .queryParam("prompt", "consent")           // 매번 동의화면 확실히 뜨게
                .encode()
                .build()
                .toUriString();

        response.sendRedirect(url);   // 브라우저를 GitHub 동의화면으로 이동


    }

    //  GitHub이 code로 검증,교환,연동
    @GetMapping("/callback")
    public void callback(@RequestParam String code,
                         @RequestParam String state,
                         HttpServletResponse response) throws IOException {
        try {
            Long userId = jwtProvider.parseGithubLinkState(state);   // state 검증 → 연동 시작한 userId
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

            String ghToken = exchangeCodeForToken(code);             // code → access token (서버에서)
            Map<String, Object> ghUser = fetchGithubUser(ghToken);   // GitHub 신원(id, login)
            String githubId = String.valueOf(ghUser.get("id"));
            String username = (String) ghUser.get("login");

            // 충돌: 이 GitHub이 이미 '다른' 계정에 연결돼 있으면 거부
            userRepository.findByGithubId(githubId).ifPresent(owner -> {
                if (!owner.getId().equals(userId)) {
                    throw new BusinessException(ErrorCode.GITHUB_ALREADY_LINKED);
                }
            });

            user.linkGithub(githubId, username, ghToken);            // 현재 계정에 붙임
            userRepository.save(user);

            response.sendRedirect(frontendUrl + "/app/my?linked=1"); // 성공 → 마이페이지
        } catch (BusinessException e) {
            response.sendRedirect(frontendUrl + "/app/my?error=" + e.getErrorCode().name().toLowerCase());
        } catch (Exception e) {
            response.sendRedirect(frontendUrl + "/app/my?error=github_link_failed");
        }
    }

    // code → access token (client_secret은 body에 담아 서버에서만 교환)
    private String exchangeCodeForToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        Map<String, Object> res = org.springframework.web.client.RestClient.create()
                .post()
                .uri("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {});
        if (res == null || res.get("access_token") == null) {
            throw new BusinessException(ErrorCode.GITHUB_LINK_FAILED);
        }
        return (String) res.get("access_token");
    }

    // GitHub 신원 조회 (id, login)
    private Map<String, Object> fetchGithubUser(String accessToken) {
        Map<String, Object> u = org.springframework.web.client.RestClient.create()
                .get()
                .uri("https://api.github.com/user")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {});
        if (u == null || u.get("id") == null) {
            throw new BusinessException(ErrorCode.GITHUB_LINK_FAILED);
        }
        return u;
    }


}
