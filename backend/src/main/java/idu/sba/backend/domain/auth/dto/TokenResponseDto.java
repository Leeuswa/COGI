package idu.sba.backend.domain.auth.dto;

import lombok.Getter;

@Getter
public class TokenResponseDto {
    // 발급된 JWT
    private final String accessToken;
    //유효시간 - 프론트에서 만료 계산 처리용
    private final long expiresIn;

    private TokenResponseDto(String accessToken, long expiresIn){
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
    }

    public static TokenResponseDto of(String accessToken, long expiresIn){
        return new TokenResponseDto(accessToken,expiresIn);
    }

}


