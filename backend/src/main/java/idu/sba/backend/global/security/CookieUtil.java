package idu.sba.backend.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    public static final String ACCESS_TOKEN = "accessToken";   // 쿠키 이름

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;   // ms

    // JWT를 담은 HttpOnly 쿠키 생성 (로그인/소셜 성공 시)
    public ResponseCookie createAccessTokenCookie(String token) {
        return ResponseCookie.from(ACCESS_TOKEN, token)
                .httpOnly(true)                        // JS 접근 불가 (XSS 방어)
                .secure(false)                         // 로컬 http라 false, 운영 https는 true
                .path("/")                             // 모든 경로에서 전송
                .maxAge(accessTokenExpiration / 1000)  // 유효기간 (초 단위)
                .sameSite("Lax")                       // CSRF 완화
                .build();
    }

    // 쿠키 삭제용 (로그아웃 시) - 같은 이름 빈 값 + maxAge 0
    public ResponseCookie deleteAccessTokenCookie() {
        return ResponseCookie.from(ACCESS_TOKEN, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)         // 0 = 즉시 만료 = 브라우저가 쿠키 삭제
                .sameSite("Lax")
                .build();
    }
}