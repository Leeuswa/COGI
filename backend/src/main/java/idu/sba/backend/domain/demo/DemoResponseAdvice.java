package idu.sba.backend.domain.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.List;

/*
 * 테스트 계정이 받는 응답에서 비어 보이는 칸을 예시 값으로 채운다. 다른 회원 응답에는 손대지 않는다.
 * DB 에는 아무것도 쓰지 않는다 — 공지처럼 모든 회원이 같이 보는 표에 예시를 넣으면 다른 회원에게도 보이기 때문.
 *
 * - 내 프로필: email 이 없으면 화면용 주소 (마이페이지 「체험 사용자 · null」 이 뜨지 않게).
 *   DB 의 email 은 그대로 NULL 이라 이 주소로는 이메일 로그인 · 비밀번호 찾기 · 메일 발송이 되지 않는다.
 *   example.com 은 어디로도 배달되지 않게 정해 둔 예약 도메인이다.
 * - 공지(마이페이지 공지 탭, 관리자 「전체 공지」 이력): 실제 공지가 하나도 없을 때만 예시 3건
 */
@RestControllerAdvice
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cogi.demo", name = "enabled", havingValue = "true")
public class DemoResponseAdvice implements ResponseBodyAdvice<Object> {

    static final String PROFILE_PATH = "/api/users/me/profile";
    static final String NOTICES_PATH = "/api/notices";
    static final String ADMIN_NOTICES_PATH = "/api/admin/notices";

    record SampleNotice(String subject, String content, int daysAgo, boolean urgent, int recipients, int failed) {}

    static final List<SampleNotice> SAMPLE_NOTICES = List.of(
            new SampleNotice("스튜디오에서 PR 을 바로 가져올 수 있어요",
                    "스튜디오의 「PR 가져오기」 에서 연동한 저장소의 열린 PR 을 고르면 변경 파일을 불러와 바로 리뷰를 받을 수 있어요. "
                            + "리뷰 결과는 PR 대시보드와 리뷰 이력에 함께 남습니다.", 3, false, 128, 0),
            new SampleNotice("주간 리포트에서 이슈를 하나씩 볼 수 있어요",
                    "주간 리포트의 발생 · 해결 건수를 누르면 그 주에 나온 지적을 저장소별로 나눠 볼 수 있어요.", 12, false, 121, 1),
            new SampleNotice("로그인 유지 시간 안내",
                    "보안을 위해 로그인은 2시간 동안 유지돼요. 화면 위 남은 시간 옆 「연장」 을 누르면 다시 2시간으로 늘어납니다.", 26, true, 117, 2));

    private final DemoAccountRegistry registry;
    private final ObjectMapper objectMapper;

    static String displayEmail(DemoKind kind) {
        return "demo-" + kind.path() + "@example.com";
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        String path = request.getURI().getPath();
        if (body == null || body instanceof String
                || !(PROFILE_PATH.equals(path) || NOTICES_PATH.equals(path) || ADMIN_NOTICES_PATH.equals(path))
                || !"GET".equals(request.getMethod().name())) {
            return body;
        }
        DemoKind kind = registry.kindOf(currentUserId());
        if (kind == null) {
            return body;
        }
        JsonNode tree = objectMapper.valueToTree(body);
        if (!(tree instanceof ObjectNode root)) {
            return body;
        }
        JsonNode data = root.get("data");
        if (PROFILE_PATH.equals(path)) {
            if (data instanceof ObjectNode profile && (profile.get("email") == null || profile.get("email").isNull())) {
                profile.put("email", displayEmail(kind));
            }
        } else if (data instanceof ArrayNode list && list.isEmpty()) {
            fillNotices(list, ADMIN_NOTICES_PATH.equals(path));
        }
        return root;
    }

    private void fillNotices(ArrayNode list, boolean adminView) {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        long id = -1; // 실제 공지 id 와 겹치지 않게 음수
        for (SampleNotice n : SAMPLE_NOTICES) {
            ObjectNode row = list.addObject();
            row.put("id", id--);
            row.put("subject", n.subject());
            row.put("content", n.content());
            String at = now.minusDays(n.daysAgo()).withHour(10).toString();
            if (adminView) {
                row.put("recipientCount", n.recipients());
                row.put("successCount", n.recipients() - n.failed());
                row.put("failCount", n.failed());
                row.put("status", "SENT");
                row.put("createdAt", at);
            } else {
                row.put("sentAt", at);
            }
            row.put("urgent", n.urgent());
        }
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Long id ? id : null;
    }
}
