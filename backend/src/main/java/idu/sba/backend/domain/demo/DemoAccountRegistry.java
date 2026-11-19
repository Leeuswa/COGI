package idu.sba.backend.domain.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/*
 * 어떤 userId 가 테스트 계정인지 기억해 두는 곳. 요청마다 DB 를 보지 않으려고 메모리에 든다.
 * 서버가 켜질 때 한 번 읽고, 테스트 로그인으로 계정을 새로 만들 때마다 다시 읽는다.
 * 테스트 계정 표시는 githubId 의 "cogi-demo:" 머리말 하나뿐이다 (DemoKind 설명 참고).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cogi.demo", name = "enabled", havingValue = "true")
public class DemoAccountRegistry {

    static final String KEY_PREFIX = "cogi-demo:";

    private final JdbcTemplate jdbc;

    // userId → 로그인 계정 종류. 팀원(mate)은 로그인 계정이 아니라 여기 없다
    private volatile Map<Long, DemoKind> accounts = Map.of();

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        Map<Long, DemoKind> found = new HashMap<>();
        jdbc.query("SELECT id, github_id FROM users WHERE github_id LIKE ?",
                rs -> {
                    String key = rs.getString("github_id");
                    for (DemoKind k : DemoKind.values()) {
                        if (k.key().equals(key)) found.put(rs.getLong("id"), k);
                    }
                }, KEY_PREFIX + "%");
        accounts = Map.copyOf(found);
        log.info("테스트 로그인 켜짐 — 이미 있는 테스트 계정 {}개", found.size());
    }

    public boolean isDemo(Long userId) {
        return userId != null && accounts.containsKey(userId);
    }

    public boolean isDemoAdmin(Long userId) {
        return userId != null && accounts.get(userId) == DemoKind.ADMIN;
    }

    public DemoKind kindOf(Long userId) {
        return userId == null ? null : accounts.get(userId);
    }

    public Set<Long> accountIds() {
        return accounts.keySet();
    }
}
