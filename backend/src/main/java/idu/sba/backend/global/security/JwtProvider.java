package idu.sba.backend.global.security;


import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
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

    private static final long TOTP_TOKEN_EXPIRATION = 5 * 60 * 1000L; // 5분


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

    // GitHub 연동용 state 토큰 유효시간 (5분)
    private static final long LINK_STATE_EXPIRATION = 5 * 60 * 1000L;

    // 연동 시작 시 발급하는 state 토큰
    public String createGithubLinkState(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + LINK_STATE_EXPIRATION);
        return Jwts.builder()
                .subject(String.valueOf(userId))   // 연동을 시작한 사용자 id
                .claim("purpose", "github_link")   // 용도 표시 (로그인 토큰과 구분)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)                     //  비밀키로 서명 → 위조 불가
                .compact();
    }

    // 콜백에서 받은 state 검증 → 연동 시작한 userId 반환 (위조·만료·용도불일치면 예외)
    public Long parseGithubLinkState(String state) {
        Claims claims = parse(state);              // 서명·만료 검증 (실패 시 예외 터짐)
        if (!"github_link".equals(claims.get("purpose", String.class))) {
            throw new BusinessException(ErrorCode.GITHUB_LINK_FAILED);
        }
        return Long.valueOf(claims.getSubject());
    }

    //2단계 인증 토큰 발급
    public String createTotpToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("purpose", "totp")   // 로그인/링크 토큰과 용도 구분
                .issuedAt(now)
                .expiration(new Date(now.getTime() + TOTP_TOKEN_EXPIRATION))
                .signWith(key)
                .compact();
    }
    // verify 단계에서 검증 → userId 반환 (위조·만료·용도불일치면 예외)
    public Long parseTotpToken(String token) {
        Claims claims = parse(token);
        if (!"totp".equals(claims.get("purpose", String.class))) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return Long.valueOf(claims.getSubject());
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
