package idu.sba.backend.global.security;

import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.entity.UserStatus;
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
    private final UserRepository userRepository;



    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Long userId = ((Number) oAuth2User.getAttribute("userId")).longValue();
        String role = (String) oAuth2User.getAttribute("role");

        User user = userRepository.findById(userId).orElse(null);

        // 정지 계정은 소셜 로그인도 차단 (이메일 로그인 AuthServiceImpl과 동일 정책) — 토큰 발급 전에 막는다
        if (user != null && user.getStatus() == UserStatus.SUSPENDED) {
            response.sendRedirect("http://localhost:5173/login?error=suspended");
            return;
        }

        // JWT 발급 → HttpOnly 쿠키
        String token = jwtProvider.createToken(userId, role);
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.createAccessTokenCookie(token).toString());

        // 신규(온보딩 미완료) 여부 → 프론트가 온보딩 보낼지 판단
        boolean isNew = user == null || !Boolean.TRUE.equals(user.getOnboardingCompleted());

        // 프론트 콜백으로 리다이렉트 (토큰은 쿠키에 있음)
        response.sendRedirect(FRONT_CALLBACK + "?isNew=" + isNew);
    }
}