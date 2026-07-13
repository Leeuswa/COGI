package idu.sba.backend.global.security;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

import static java.util.Date.parse;

@Component
public class JwtProvider {

    //jwt key
    private final SecretKey key;
    //토큰 유효시간
    private final long accessTokenExpiration;


    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration){
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
    }

    //토큰 발급
    public String createToken(Long userId,String role){
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration); // 현재시각 + 유효시간

        return Jwts.builder()
                .subject(String.valueOf(userId)) //토큰 사용자
                .claim("role",role) // 권한
                .issuedAt(now) //발급 시각
                .expiration(expiry) //만료 시각
                .signWith(key) //비밀키로 서(위조 방지)
                .compact(); //최종 문자열 압

    }
    //토큰에서 userId 꺼내 + 검증
    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    //토큰에서 role 꺼내기
    public String getRole(String token){
        return parse(token).get("role",String.class);
    }

    //토큰 유효성 검증: 키가 맞고,만료가 안됐으면 true, 틀리면 false
    public boolean validateToken(String token){
        try{
            parse(token);
            return true;
        } catch (Exception e){
            return false; //서명불일치,토큰만료,형식오류
        }

    }

    //토큰을 검증하며 payload(Claims) 꺼냄
    private Claims parse(String token){
        return Jwts.parser()
                .verifyWith(key) //비밀키로 서명검증
                .build()
                .parseSignedClaims(token) //검증 + 피싱 (실패 시 예외)
                .getPayload(); //내용(Claims) 반환
    }
}
