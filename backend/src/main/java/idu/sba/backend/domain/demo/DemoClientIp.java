package idu.sba.backend.domain.demo;

import jakarta.servlet.http.HttpServletRequest;

/*
 * 횟수 제한에 쓰는 접속 주소.
 * 운영은 프록시(Caddy) 뒤라 remoteAddr 가 프록시 주소 하나로 모인다. 프록시가 덧붙이는
 * X-Forwarded-For 의 마지막 값(프록시가 직접 본 주소)을 쓴다 — 앞쪽 값은 사용자가 마음대로 넣을 수 있다.
 */
final class DemoClientIp {

    private DemoClientIp() {}

    static String of(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
