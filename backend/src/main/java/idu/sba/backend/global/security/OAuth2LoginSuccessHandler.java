package idu.sba.backend.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        // 1) CustomOAuth2UserService가 담아둔 정보 꺼내기
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Long userId = ((Number) oAuth2User.getAttribute("userId")).longValue();
        String role = (String) oAuth2User.getAttribute("role");

        // 2) JWT 발급 (이메일 로그인과 동일한 JwtProvider 사용)
        String token = jwtProvider.createToken(userId, role);

        // 3) JSON으로 토큰 응답
        String json = String.format(
                "{\"success\":true,\"message\":\"소셜 로그인 성공\"," +
                        "\"data\":{\"accessToken\":\"%s\",\"expiresIn\":%d}}",
                token, accessTokenExpiration);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json);
    }
}