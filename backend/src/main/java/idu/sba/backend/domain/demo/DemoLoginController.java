package idu.sba.backend.domain.demo;

import idu.sba.backend.global.common.ApiResponse;
import idu.sba.backend.global.security.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 테스트 로그인 길. /api/auth/** 는 보안 설정에서 이미 열려 있어 따로 열 줄이 없다.
 * COGI_DEMO_ENABLED=true 일 때만 이 컨트롤러가 생긴다. 꺼져 있으면 두 주소 모두 404.
 *
 * 비밀번호는 어디에도 없다. 버튼을 누르면 서버가 그 계정 데이터를 처음 상태로 되돌리고 바로 토큰 쿠키를 내준다.
 */
@RestController
@RequestMapping("/api/auth/demo")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cogi.demo", name = "enabled", havingValue = "true")
public class DemoLoginController {

    private static final long MIN_INTERVAL_MS = 3_000; // 같은 곳에서 연달아 누르면 되돌리기만 반복되니 3초 간격

    private final DemoLoginService demoLoginService;
    private final CookieUtil cookieUtil;
    private final Map<String, Long> lastClick = new ConcurrentHashMap<>();

    // 로그인 화면이 버튼을 보일지 정할 때 부른다
    @GetMapping
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of("enabled", true));
    }

    @PostMapping("/{kind}")
    public ResponseEntity<ApiResponse<?>> login(@PathVariable String kind,
                                                HttpServletRequest request,
                                                HttpServletResponse response) throws InterruptedException {
        DemoKind k = DemoKind.fromPath(kind);
        if (k == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("없는 테스트 계정입니다."));
        }
        if (tooSoon(DemoClientIp.of(request))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.fail("잠시 후 다시 시도해주세요."));
        }
        String token = demoLoginService.resetAndIssueToken(k);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.fail("테스트 계정을 준비하는 중입니다. 잠시 후 다시 시도해주세요."));
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.createAccessTokenCookie(token).toString());
        // 이메일 로그인과 같은 모양으로 답한다 — 화면은 그 뒤 프로필을 받아 세션을 연다
        return ResponseEntity.ok(ApiResponse.ok("로그인 성공", Map.of("totpRequired", false)));
    }

    private boolean tooSoon(String ip) {
        long now = System.currentTimeMillis();
        if (lastClick.size() > 10_000) lastClick.clear(); // 오래 켜져 있어도 끝없이 쌓이지 않게
        Long prev = lastClick.put(ip, now);
        return prev != null && now - prev < MIN_INTERVAL_MS;
    }
}
