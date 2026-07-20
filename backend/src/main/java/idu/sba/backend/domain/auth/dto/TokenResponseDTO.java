package idu.sba.backend.domain.auth.dto;

import lombok.Getter;

@Getter
public class TokenResponseDTO {
    // 발급된 JWT
    private final String accessToken;
    //유효시간 - 프론트에서 만료 계산 처리용
    private final long expiresIn;

    private final boolean totpRequired; // true면 아직 로그인 완료 아님(2차 필요)
    private final String tempToken;     // totpRequired일 때만 채워짐


    private TokenResponseDTO(String accessToken, long expiresIn, boolean totpRequired, String tempToken){
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
        this.totpRequired = totpRequired;
        this.tempToken = tempToken;
    }
    // 일반 로그인 성공
    public static TokenResponseDTO of(String accessToken, long expiresIn){
        return new TokenResponseDTO(accessToken, expiresIn, false, null);
    }
    // 2차 인증 대기 (JWT 아직 안 줌)
    public static TokenResponseDTO totpChallenge(String tempToken){
        return new TokenResponseDTO(null, 0, true, tempToken);
    }
}


