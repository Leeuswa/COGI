package idu.sba.backend.domain.demo;

import idu.sba.backend.domain.pr.dto.PrFileResponseDTO;
import idu.sba.backend.domain.pr.dto.PrSummaryResponseDTO;
import idu.sba.backend.domain.repo.client.GithubPrFileDto;
import idu.sba.backend.domain.repo.client.GithubPrSummaryDto;
import idu.sba.backend.domain.repo.client.GithubRepoDto;
import idu.sba.backend.domain.repo.dto.GithubRepoResponseDTO;
import idu.sba.backend.global.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * 테스트 계정 요청만 골라 지키는 필터. 다른 회원 요청은 아무것도 하지 않고 그대로 넘긴다.
 * 스프링 시큐리티 필터 뒤에서 돌아서, 여기 올 때는 토큰 검사가 끝나 SecurityContext 에 userId 가 들어 있다.
 *
 * 1) 테스트 관리자: 읽기(GET)만 된다. 바꾸는 요청은 전부 403 (로그아웃·세션 연장만 예외)
 * 2) 테스트 계정 공통: 실제 돈·메일·외부 계정이 걸린 요청은 403 (결제, 탈퇴, 비밀번호, 2단계 인증, GitHub·카카오 연결, 초대 메일, 문의)
 * 3) AI 를 부르는 요청: 하루 횟수 제한 (접속 주소별 + 전체). 넘으면 크레딧 소진과 같은 429 응답
 * 4) GitHub 에서 실시간으로 읽는 화면(스튜디오 PR 가져오기): GitHub 대신 예시 응답 — 실제 GitHub 을 부르지 않는다
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cogi.demo", name = "enabled", havingValue = "true")
public class DemoGuardFilter extends OncePerRequestFilter {

    static final int AI_LIMIT_PER_IP = 10;
    static final int AI_LIMIT_TOTAL = 60;

    static final String MSG_ADMIN_READ_ONLY = "테스트 관리자 계정은 보기만 할 수 있어요. 바꾸는 기능은 막혀 있습니다.";
    static final String MSG_AI_LIMIT = "오늘의 리뷰 요청 한도를 모두 사용했습니다.";

    private record Rule(String method, Pattern path, String message) {}

    private static Rule rule(String method, String regex, String message) {
        return new Rule(method, Pattern.compile(regex), message);
    }

    private static final String PAY = "테스트 계정에서는 실제 결제를 진행할 수 없어요.";
    private static final List<Rule> BLOCKED = List.of(
            rule("*", "^/api/payments(/.*)?$", PAY),
            rule("POST", "^/api/subscriptions(/.*)?$", PAY),
            rule("PATCH", "^/api/subscriptions/.*$", PAY),
            rule("DELETE", "^/api/subscriptions/.*$", PAY),
            rule("DELETE", "^/api/users/me$", "테스트 계정은 탈퇴할 수 없어요."),
            rule("PATCH", "^/api/users/me/password$", "테스트 계정은 비밀번호를 바꿀 수 없어요."),
            rule("POST", "^/api/users/me/totp/.*$", "테스트 계정에서는 2단계 인증을 설정할 수 없어요."),
            rule("POST", "^/api/users/me/github-link$", "테스트 계정은 GitHub 계정을 연결할 수 없어요."),
            rule("POST", "^/api/repos/[^/]+/link$", "테스트 계정은 실제 GitHub 저장소를 연동할 수 없어요."),
            rule("POST", "^/api/repos/\\d+/members/invite$", "테스트 계정에서는 초대 메일을 보낼 수 없어요."),
            rule("POST", "^/api/calendar/kakao$", "테스트 계정은 카카오 캘린더를 연결할 수 없어요. 구글 캘린더나 .ics 내려받기를 써 주세요."),
            rule("POST", "^/api/inquiries$", "테스트 계정에서는 문의를 남길 수 없어요."));

    private static final List<Pattern> AI_CALLS = List.of(
            Pattern.compile("^/api/reviews/paste$"),
            Pattern.compile("^/api/reviews/upload$"),
            Pattern.compile("^/api/repos/\\d+/prs/\\d+/review$"),
            Pattern.compile("^/api/reviews/\\d+/questions$"),
            Pattern.compile("^/api/issues/\\d+/reverify$"),
            Pattern.compile("^/api/learning-cards$"),
            Pattern.compile("^/api/learning-cards/\\d+/quiz$"),
            Pattern.compile("^/api/learning-cards/\\d+/study-plan$"),
            Pattern.compile("^/api/ai-skill-recommendations(/by-weakness)?$"));

    private static final List<Pattern> ADMIN_WRITABLE = List.of(
            Pattern.compile("^/api/auth/(logout|refresh)$"),
            Pattern.compile("^/api/auth/demo(/.*)?$"),
            Pattern.compile("^/api/learning-cards/plan-notifications/today$")); // 대시보드가 자기 알림을 만드는 호출

    private static final Pattern GH_REPOS = Pattern.compile("^/api/repos/github$");
    private static final Pattern GH_PRS = Pattern.compile("^/api/repos/(\\d+)/prs$");
    private static final Pattern GH_PR_FILES = Pattern.compile("^/api/repos/(\\d+)/prs/(\\d+)/files$");

    private final DemoAccountRegistry registry;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    // AI 호출 횟수 — 날짜가 바뀌면 처음부터. 서버를 다시 켜도 처음부터 (하루 한도라 그 정도는 받아들인다)
    private volatile LocalDate countDay = LocalDate.now();
    private final Map<String, AtomicInteger> perIp = new ConcurrentHashMap<>();
    private final AtomicInteger total = new AtomicInteger();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long userId = currentUserId();
        if (!registry.isDemo(userId)) {
            chain.doFilter(request, response);
            return;
        }
        String method = request.getMethod();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean read = "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);

        if (!read && registry.isDemoAdmin(userId) && ADMIN_WRITABLE.stream().noneMatch(p -> p.matcher(path).matches())) {
            write(response, HttpStatus.FORBIDDEN, ApiResponse.fail(MSG_ADMIN_READ_ONLY));
            return;
        }
        if (!read) {
            for (Rule r : BLOCKED) {
                if (("*".equals(r.method()) || r.method().equals(method)) && r.path().matcher(path).matches()) {
                    write(response, HttpStatus.FORBIDDEN, ApiResponse.fail(r.message()));
                    return;
                }
            }
            if ("POST".equals(method) && AI_CALLS.stream().anyMatch(p -> p.matcher(path).matches())
                    && !tryCountAiCall(DemoClientIp.of(request))) {
                write(response, HttpStatus.TOO_MANY_REQUESTS, ApiResponse.fail(MSG_AI_LIMIT));
                return;
            }
        }
        if ("GET".equals(method) && serveGithubSample(userId, path, response)) {
            return;
        }
        chain.doFilter(request, response);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Long id ? id : null;
    }

    boolean tryCountAiCall(String ip) {
        LocalDate today = LocalDate.now();
        if (!today.equals(countDay)) {
            synchronized (this) {
                if (!today.equals(countDay)) {
                    perIp.clear();
                    total.set(0);
                    countDay = today;
                }
            }
        }
        AtomicInteger mine = perIp.computeIfAbsent(ip, k -> new AtomicInteger());
        if (mine.incrementAndGet() > AI_LIMIT_PER_IP) {
            mine.decrementAndGet();
            return false;
        }
        if (total.incrementAndGet() > AI_LIMIT_TOTAL) {
            total.decrementAndGet();
            mine.decrementAndGet();
            return false;
        }
        return true;
    }

    // ── GitHub 대신 예시 응답 ──
    // 실제 화면이 받는 것과 같은 DTO 를 같은 변환 함수(of)로 만들어 모양이 어긋나지 않게 한다
    private boolean serveGithubSample(Long userId, String path, HttpServletResponse response) throws IOException {
        if (GH_REPOS.matcher(path).matches()) {
            List<GithubRepoResponseDTO> repos = new ArrayList<>();
            Long base = repoIdBase(userId);
            if (base == null) return false;
            String[] names = {DemoSeedData.REPO_NAMES[0], DemoSeedData.REPO_NAMES[1], DemoSeedData.UNLINKED_REPO_NAME};
            for (int i = 0; i < names.length; i++) {
                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("id", base + i + 1);
                raw.put("name", names[i]);
                raw.put("private", i == 1);
                raw.put("full_name", "cogi-demo/" + names[i]);
                repos.add(GithubRepoResponseDTO.of(objectMapper.convertValue(raw, GithubRepoDto.class)));
            }
            write(response, HttpStatus.OK, ApiResponse.ok(repos));
            return true;
        }
        Matcher prs = GH_PRS.matcher(path);
        if (prs.matches() && ownsRepo(userId, Long.valueOf(prs.group(1)))) {
            List<PrSummaryResponseDTO> list = new ArrayList<>();
            for (DemoSeedData.OpenPr pr : DemoSeedData.OPEN_PRS) {
                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("number", pr.number());
                raw.put("title", pr.title());
                raw.put("user", pr.author() == 0 ? null : Map.of("login", mateLogin(userId, pr.author())));
                list.add(PrSummaryResponseDTO.of(objectMapper.convertValue(raw, GithubPrSummaryDto.class)));
            }
            write(response, HttpStatus.OK, ApiResponse.ok(list));
            return true;
        }
        Matcher files = GH_PR_FILES.matcher(path);
        if (files.matches() && ownsRepo(userId, Long.valueOf(files.group(1)))) {
            int number = Integer.parseInt(files.group(2));
            List<PrFileResponseDTO> list = new ArrayList<>();
            DemoSeedData.OPEN_PRS.stream().filter(p -> p.number() == number).findFirst().ifPresent(pr -> {
                for (String[] f : pr.files()) {
                    Map<String, Object> raw = Map.of("filename", f[0], "status", "added", "patch", f[1]);
                    list.add(PrFileResponseDTO.of(objectMapper.convertValue(raw, GithubPrFileDto.class)));
                }
            });
            write(response, HttpStatus.OK, ApiResponse.ok(list));
            return true;
        }
        return false;
    }

    private Long repoIdBase(Long userId) {
        DemoKind kind = registry.kindOf(userId);
        return kind == null ? null : kind.githubRepoIdBase();
    }

    private String mateLogin(Long userId, int author) {
        DemoKind kind = registry.kindOf(userId);
        return kind == null ? null : kind.mates().get(author - 1).githubUsername();
    }

    // 예시 응답은 테스트 계정이 연 저장소에만. 다른 저장소 번호면 원래 길(멤버 확인 → 미연동 안내)로 보낸다
    private boolean ownsRepo(Long userId, Long repoId) {
        List<Long> owner = jdbc.queryForList("SELECT user_id FROM repositories WHERE id = ?", Long.class, repoId);
        return !owner.isEmpty() && userId.equals(owner.get(0));
    }

    private void write(HttpServletResponse response, HttpStatus status, Object body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
