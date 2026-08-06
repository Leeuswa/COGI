package idu.sba.backend.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TotpSetupResponseDTO {

    private final String secret; // 앱에 수동 입력할 시크릿
    private final String qrCodeUrl; //QR

}
