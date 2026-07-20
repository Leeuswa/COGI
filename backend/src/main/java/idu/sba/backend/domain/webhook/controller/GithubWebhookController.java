package idu.sba.backend.domain.webhook.controller;

import idu.sba.backend.domain.webhook.dto.GithubPullRequestEventPayload;
import idu.sba.backend.domain.webhook.security.GithubWebhookSignatureVerifier;
import idu.sba.backend.domain.webhook.service.GithubWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

//API-024: GitHub Webhook 수신(PR 생성/업데이트) → Diff 추출 → 분석 대기열 전달
//JWT 인증 없음(SecurityConfig에서 permitAll) — X-Hub-Signature-256 서명 검증이 이 엔드포인트의 인증.
//실제 GitHub에 이 URL을 웹훅으로 자동 등록하는 것(공개 콜백 URL 필요)은 이번 범위 밖 — 수신기만 구현.
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class GithubWebhookController {

    private static final Set<String> HANDLED_ACTIONS = Set.of("opened", "synchronize", "reopened");

    private final GithubWebhookSignatureVerifier signatureVerifier;
    private final GithubWebhookService githubWebhookService;
    private final ObjectMapper objectMapper;

    @PostMapping("/github")
    public ResponseEntity<Void> receiveGithubWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType) throws IOException {

        //raw body를 바이트 그대로 읽는다 — @RequestBody로 JSON 파싱을 거치면 공백/순서가 바뀌어
        //서명 계산이 원본과 어긋날 위험이 있다.
        String rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        if (!signatureVerifier.isValid(rawBody, signature)) {
            log.warn("GitHub webhook 서명 검증 실패");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!"pull_request".equals(eventType)) {
            return ResponseEntity.ok().build(); //ping 등 관심 없는 이벤트 — 수신 확인만
        }

        GithubPullRequestEventPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, GithubPullRequestEventPayload.class);
        } catch (Exception e) {
            log.error("GitHub webhook payload 파싱 실패", e);
            return ResponseEntity.ok().build(); //같은 바이트를 재전송해도 안 고쳐지므로 재시도 유도 안 함
        }
        if (!HANDLED_ACTIONS.contains(payload.action())) {
            return ResponseEntity.ok().build();
        }

        githubWebhookService.handlePullRequestEvent(payload);
        return ResponseEntity.ok().build();
    }

}
