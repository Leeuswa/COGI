package idu.sba.backend.domain.webhook.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

//GitHub Webhook 요청의 X-Hub-Signature-256 헤더 검증(API-024) — JWT 대신 이 서명 자체가 인증.
//반드시 raw body(바이트 그대로)로 계산해야 함 — Jackson을 거치면 공백/순서가 바뀌어 서명이 어긋날 수 있음.
@Component
public class GithubWebhookSignatureVerifier {

    private final String secret;

    public GithubWebhookSignatureVerifier(@Value("${github.webhook.secret}") String secret) {
        this.secret = secret;
    }

    public boolean isValid(String rawBody, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        String expected = "sha256=" + hmacSha256Hex(rawBody);
        //타이밍 공격 방지를 위해 상수 시간 비교
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha256Hex(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Webhook 서명 계산 실패", e);
        }
    }

}
