package idu.sba.backend.domain.webhook.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class GithubWebhookSignatureVerifierTest {

    private static final String SECRET = "test-secret";
    private final GithubWebhookSignatureVerifier verifier = new GithubWebhookSignatureVerifier(SECRET);

    private String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void 올바른_서명이면_통과() {
        String body = "{\"action\":\"opened\"}";
        assertThat(verifier.isValid(body, sign(body, SECRET))).isTrue();
    }

    @Test
    void body가_변조되면_실패() {
        String body = "{\"action\":\"opened\"}";
        String signatureForOriginal = sign(body, SECRET);

        assertThat(verifier.isValid("{\"action\":\"closed\"}", signatureForOriginal)).isFalse();
    }

    @Test
    void 다른_시크릿으로_서명하면_실패() {
        String body = "{\"action\":\"opened\"}";
        assertThat(verifier.isValid(body, sign(body, "wrong-secret"))).isFalse();
    }

    @Test
    void 헤더가_없으면_실패() {
        assertThat(verifier.isValid("{}", null)).isFalse();
    }

    @Test
    void sha256_접두사가_없으면_실패() {
        String body = "{}";
        String withoutPrefix = sign(body, SECRET).replace("sha256=", "");
        assertThat(verifier.isValid(body, withoutPrefix)).isFalse();
    }

}
