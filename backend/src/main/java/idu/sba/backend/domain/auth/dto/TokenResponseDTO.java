package idu.sba.backend.domain.auth.dto;

import lombok.Getter;

@Getter
public class TokenResponseDTO {
    // 발급된 JWT
    private final String accessToken;
    //유효시간 - 프론트에서 만료 계산 처리용
    private final long expiresIn;

    private TokenResponseDTO(String accessToken, long expiresIn){
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
    }

    public static TokenResponseDTO of(String accessToken, long expiresIn){
        return new TokenResponseDTO(accessToken,expiresIn);
    }

}


