package idu.sba.backend.global.security;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

/**
 * TOTP(RFC 6238) 2차 인증 시크릿 발급·코드 검증.
 * 시크릿은 사용자별로 발급해 users.totp_secret 에 저장한다.
 */

@Component
public class TotpService {

    // 인증앱에 뜨는 발급자명
    private static final String ISSUER = "COGI";

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();

    // 기기·서버 시계 오차를 흡수하도록 앞뒤 시간창을 허용하는 기본 검증기
    private final CodeVerifier codeVerifier =
            new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());


    public String generateSecret() {
        return secretGenerator.generate();
    }
    // 인증 앱 등록용 otpauth URI (QR 인코딩 또는 시크릿 수동 입력)
    public String otpauthUri(String secret, String email){
        return new QrData.Builder()
                .label(email)   // 앱에 표시될 계정 라벨
                .secret(secret) //발급한 시크릿
                .issuer(ISSUER) //발급자
                .algorithm(HashingAlgorithm.SHA1) //표준 해시
                .digits(6) //코드 자릿수
                .period(60) //갱신 주(초)
                .build()
                .getUri();

    }

    public String qrDataUri(String secret, String email) {
        QrData data = new QrData.Builder()
                .label(email).secret(secret).issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1).digits(6).period(30).build();
        try {
            byte[] png = qrGenerator.generate(data);
            return getDataUriForImage(png, qrGenerator.getImageMimeType());
        } catch (QrGenerationException e) {
            throw new BusinessException(ErrorCode.TOTP_QR_FAILED);
        }
    }

    //사용자와 입력한 6자리가 시크릿과 맞는지
    public boolean verify(String secret, String code){
        return codeVerifier.isValidCode(secret,code);
    }




}
