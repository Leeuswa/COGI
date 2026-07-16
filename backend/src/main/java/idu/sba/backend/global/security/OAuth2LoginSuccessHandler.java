package idu.sba.backend.global.security;

import idu.sba.backend.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String FRONT_CALLBACK = "http://localhost:5173/oauth/callback";
    private final JwtProvider jwtProvider;
    private  final CookieUtil cookieUtil;
    private UserRepository userRepository;



    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Long userId = ((Number) oAuth2User.getAttribute("userId")).longValue();
        String role = (String) oAuth2User.getAttribute("role");

        // JWT 발급 → HttpOnly 쿠키
        String token = jwtProvider.createToken(userId, role);
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.createAccessTokenCookie(token).toString());

        // 신규(온보딩 미완료) 여부 → 프론트가 온보딩 보낼지 판단
        boolean isNew = userRepository.findById(userId)
                .map(user -> !Boolean.TRUE.equals(user.getOnboardingCompleted()))
                .orElse(true);

        // 프론트 콜백으로 리다이렉트 (토큰은 쿠키에 있음)
        response.sendRedirect(FRONT_CALLBACK + "?isNew=" + isNew);
    }
}