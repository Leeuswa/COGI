package idu.sba.backend.domain.demo;

import idu.sba.backend.domain.pr.dto.PrFileResponseDTO;
import idu.sba.backend.domain.pr.dto.PrSummaryResponseDTO;
import idu.sba.backend.domain.repo.client.GithubPrFileDto;
import idu.sba.backend.domain.repo.client.GithubPrSummaryDto;
import idu.sba.backend.domain.repo.client.GithubRepoDto;
import idu.sba.backend.domain.repo.dto.GithubRepoResponseDTO;
import idu.sba.backend.domain.repo.dto.RepoFileResponseDTO;
import idu.sba.backend.domain.repo.dto.RepoLinkResponseDTO;
import idu.sba.backend.domain.repo.entity.GithubRepository;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.repo.service.RepoMemberService;
import idu.sba.backend.global.exception.ErrorCode;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * 1) 테스트 관리자: 관리자 콘솔(/api/admin/**)은 읽기(GET)만 된다. 그 밖의 사용자 기능은 아래 2)~4) 를 유저와 같이 따른다
 * 2) 테스트 계정 공통: 실제 돈·메일·외부 계정이 걸린 요청은 403 (결제, 탈퇴, 비밀번호, 2단계 인증, GitHub·카카오 연결, 초대 메일, 문의)
 *    GitHub 계정 연동(authorize · callback)은 페이지 이동이라 403 대신 마이페이지 안내로 돌려보낸다
 * 3) AI 를 부르는 요청: 하루 횟수 제한 (접속 주소별 + 전체). 넘으면 크레딧 소진과 같은 429 응답
 * 4) GitHub 이 있어야 되는 기능: GitHub 대신 예시 응답 — 실제 GitHub 으로는 아무 요청도 가지 않는다
 *    읽기: 내 GitHub 저장소 목록, 열린 PR, PR 변경 파일, 미리보기 파일
 *    쓰기: 예시 저장소 연동(우리 DB 에만 저장, 웹훅 등록 없음), 팀 초대(저장·메일 없이 보낸 것처럼 답함)
 *    초대 수락·거절은 테스트 계정끼리 만든 저장소의 초대만 된다
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

    // 테스트 관리자가 바꿀 수 없는 곳 = 관리자 콘솔 전부 (다른 회원 · 공지 · 약관 · FAQ · 리뷰 지침을 바꾸는 길).
    // 자기 학습 카드 · 북마크 같은 사용자 기능은 유저 테스트 계정과 같이 되고, 다시 로그인하면 되돌아간다
    private static final Pattern ADMIN_CONSOLE = Pattern.compile("^/api/admin(/.*)?$");

    private static final Pattern GH_REPOS = Pattern.compile("^/api/repos/github$");
    private static final Pattern GH_PRS = Pattern.compile("^/api/repos/(\\d+)/prs$");
    private static final Pattern GH_PR_FILES = Pattern.compile("^/api/repos/(\\d+)/prs/(\\d+)/files$");
    private static final Pattern GH_FILE = Pattern.compile("^/api/repos/(\\d+)/file-content$");
    private static final Pattern GH_LINK = Pattern.compile("^/api/repos/([^/]+)/link$");
    private static final Pattern INVITE = Pattern.compile("^/api/repos/(\\d+)/members/invite$");
    private static final Pattern INVITE_ANSWER = Pattern.compile("^/api/repos/(\\d+)/members/invitations/\\d+/(accept|reject)$");

    // 마이페이지 연동 버튼은 fetch 가 아니라 페이지 이동(GET)이라 JSON 대신 마이페이지로 돌려보낸다.
    // 연동되면 githubId 의 cogi-demo: 표시가 사라져 테스트 계정이 역할을 가진 채 일반 계정으로 남는다
    private static final Pattern GH_ACCOUNT_LINK = Pattern.compile("^/api/users/me/github/(authorize|callback)$");
    static final String DEMO_LINK_DENIED_REDIRECT = "/app/my?error=demo_account";

    static final String MSG_REAL_REPO = "테스트 계정은 실제 GitHub 저장소를 연동할 수 없어요.";
    static final String MSG_REAL_INVITE = "테스트 계정은 다른 회원의 팀 초대에 답할 수 없어요.";

    private final DemoAccountRegistry registry;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final RepoMemberService repoMemberService;
    private final PlatformTransactionManager transactionManager;

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

        if (GH_ACCOUNT_LINK.matcher(path).matches()) {
            response.sendRedirect(DEMO_LINK_DENIED_REDIRECT);
            return;
        }
        if (!read && registry.isDemoAdmin(userId) && ADMIN_CONSOLE.matcher(path).matches()) {
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
            if ("POST".equals(method) && serveGithubWrite(userId, path, request, response)) {
                return;
            }
        }
        if ("GET".equals(method) && serveGithubSample(userId, path, request, response)) {
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
    private boolean serveGithubSample(Long userId, String path, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (GH_REPOS.matcher(path).matches()) {
            List<GithubRepoResponseDTO> repos = new ArrayList<>();
            Long base = repoIdBase(userId);
            if (base == null) return false;
            String[] names = sampleRepoNames();
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
                raw.put("user", Map.of("login", pr.author() == 0 ? accountLogin(userId) : mateLogin(userId, pr.author())));
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
        Matcher file = GH_FILE.matcher(path);
        if (file.matches() && ownsRepo(userId, Long.valueOf(file.group(1)))) {
            write(response, HttpStatus.OK, ApiResponse.ok(sampleFile(String.valueOf(request.getParameter("path")))));
            return true;
        }
        return false;
    }

    // 미리보기가 모자란 파일을 채울 때 부르는 길 — CSS·JS 는 짧은 예시 글, 그 밖(그림 등)은 1×1 투명 PNG
    static RepoFileResponseDTO sampleFile(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".css")) {
            String css = "/* 예시 저장소의 " + path + " */\nbody { font-family: sans-serif; margin: 0; }\n";
            return RepoFileResponseDTO.ofText(path, css, css.length());
        }
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) {
            String js = "// 예시 저장소의 " + path + "\n";
            return RepoFileResponseDTO.ofText(path, js, js.length());
        }
        return RepoFileResponseDTO.ofBase64(path, TRANSPARENT_PNG, 68);
    }

    private static final String TRANSPARENT_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

    // ── GitHub 이 있어야 되는 쓰기: 실제 GitHub 대신 우리 DB 와 예시 응답으로 끝까지 가게 한다 ──
    private boolean serveGithubWrite(Long userId, String path, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Matcher link = GH_LINK.matcher(path);
        if (link.matches()) {
            linkSampleRepo(userId, link.group(1), response);
            return true;
        }
        Matcher invite = INVITE.matcher(path);
        if (invite.matches()) {
            if (!ownsRepo(userId, Long.valueOf(invite.group(1)))) {
                return false; // 내 저장소가 아니면 원래 길 — 팀장 확인에서 막힌다
            }
            // 실제로 저장하거나 메일을 보내지 않는다 — 테스트 계정의 초대가 다른 회원에게 닿지 않게
            JsonNode body = objectMapper.readTree(request.getInputStream());
            Map<String, Object> sent = new LinkedHashMap<>();
            sent.put("id", null);
            sent.put("invitedGithubUsername", body.path("githubUsername").asString(""));
            sent.put("status", "PENDING");
            sent.put("viaEmail", !body.path("email").asString("").isBlank());
            sent.put("createdAt", LocalDateTime.now().toString());
            write(response, HttpStatus.CREATED, ApiResponse.ok("초대를 보냈습니다.", sent));
            return true;
        }
        Matcher answer = INVITE_ANSWER.matcher(path);
        if (answer.matches() && !isDemoRepo(Long.valueOf(answer.group(1)))) {
            write(response, HttpStatus.FORBIDDEN, ApiResponse.fail(MSG_REAL_INVITE));
            return true;
        }
        return false;
    }

    // GitHub 목록의 예시 저장소만 연동된다. 실제 연동과 같은 행(저장소 + 팀장)을 만들고 웹훅은 걸지 않는다.
    // 다시 테스트 로그인하면 DemoSeeder.wipe 가 지워 처음처럼 미연동으로 돌아간다
    private void linkSampleRepo(Long userId, String githubRepoId, HttpServletResponse response) throws IOException {
        String[] names = sampleRepoNames();
        Long base = repoIdBase(userId);
        int index = -1;
        for (int i = 0; base != null && i < names.length; i++) {
            if (String.valueOf(base + i + 1).equals(githubRepoId)) index = i;
        }
        if (index < 0) {
            write(response, HttpStatus.FORBIDDEN, ApiResponse.fail(MSG_REAL_REPO));
            return;
        }
        if (githubRepositoryRepository.existsByGithubRepoId(githubRepoId)) {
            write(response, HttpStatus.CONFLICT, ApiResponse.fail(ErrorCode.REPO_ALREADY_LINKED.getMessage()));
            return;
        }
        String name = names[index];
        boolean isPrivate = index == 1;
        GithubRepository saved = new TransactionTemplate(transactionManager).execute(status -> {
            GithubRepository repo = githubRepositoryRepository.save(
                    GithubRepository.link(userId, githubRepoId, name, isPrivate, "cogi-demo/" + name));
            repoMemberService.registerOwner(repo.getId(), userId);
            return repo;
        });
        write(response, HttpStatus.CREATED, ApiResponse.ok("레포를 연동했습니다.", RepoLinkResponseDTO.of(saved)));
    }

    static String[] sampleRepoNames() {
        return new String[]{DemoSeedData.REPO_NAMES[0], DemoSeedData.REPO_NAMES[1], DemoSeedData.UNLINKED_REPO_NAME};
    }

    // 테스트 계정이나 가상 팀원이 만든 저장소인지
    private boolean isDemoRepo(Long repoId) {
        List<String> owner = jdbc.queryForList(
                "SELECT u.github_id FROM repositories r JOIN users u ON u.id = r.user_id WHERE r.id = ?", String.class, repoId);
        return !owner.isEmpty() && owner.get(0) != null && owner.get(0).startsWith(DemoAccountRegistry.KEY_PREFIX);
    }

    private Long repoIdBase(Long userId) {
        DemoKind kind = registry.kindOf(userId);
        return kind == null ? null : kind.githubRepoIdBase();
    }

    private String accountLogin(Long userId) {
        DemoKind kind = registry.kindOf(userId);
        return kind == null ? null : kind.githubUsername();
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
