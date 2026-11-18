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

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/*
 * 테스트 관리자가 관리자 화면(/api/admin/**)을 볼 때 다른 회원의 개인정보를 가린다.
 * 화면 모양은 그대로 두고 값만 바꾼다 — 이메일은 앞 글자와 도메인 끝만, 닉네임은 첫 글자만,
 * 문의 제목·본문·답변은 통째로 가린다(본문에 연락처 같은 게 들어 있을 수 있어서).
 * 진짜 관리자 응답에는 손대지 않는다.
 */
@RestControllerAdvice
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cogi.demo", name = "enabled", havingValue = "true")
public class DemoAdminMaskingAdvice implements ResponseBodyAdvice<Object> {

    private static final Set<String> EMAIL_KEYS = Set.of("email", "askerEmail", "inviteeEmail");
    private static final Set<String> NAME_KEYS = Set.of("nickname", "askerNickname", "githubUsername", "authorLogin");
    private static final Map<String, String> HIDDEN_TEXT = Map.of(
            "title", "(테스트 관리자에게는 가려집니다)",
            "content", "(테스트 관리자에게는 가려집니다)",
            "answer", "(테스트 관리자에게는 가려집니다)");

    private final DemoAccountRegistry registry;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        String path = request.getURI().getPath();
        if (body == null || body instanceof String || !path.startsWith("/api/admin/") || !registry.isDemoAdmin(currentUserId())) {
            return body;
        }
        JsonNode tree = objectMapper.valueToTree(body);
        mask(tree, path.startsWith("/api/admin/inquiries"));
        return tree;
    }

    private void mask(JsonNode node, boolean inquiry) {
        if (node instanceof ArrayNode arr) {
            arr.forEach(child -> mask(child, inquiry));
            return;
        }
        if (!(node instanceof ObjectNode obj)) return;
        for (String key : new ArrayList<>(obj.propertyNames())) {
            JsonNode v = obj.get(key);
            if (v != null && v.isString()) {
                String s = v.asString();
                if (EMAIL_KEYS.contains(key)) obj.put(key, maskEmail(s));
                else if (NAME_KEYS.contains(key)) obj.put(key, maskName(s));
                else if (inquiry && HIDDEN_TEXT.containsKey(key)) obj.put(key, HIDDEN_TEXT.get(key));
            } else if (v != null && v.isContainer()) {
                mask(v, inquiry);
            }
        }
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String domain = email.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        String tld = dot >= 0 ? domain.substring(dot) : "";
        return email.charAt(0) + "***@***" + tld;
    }

    static String maskName(String name) {
        if (name.isEmpty()) return name;
        return name.codePoints().limit(1)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append) + "**";
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Long id ? id : null;
    }
}
