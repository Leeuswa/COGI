package idu.sba.backend.global.config;

import idu.sba.backend.domain.admin.entity.Faq;
import idu.sba.backend.domain.admin.entity.Notice;
import idu.sba.backend.domain.admin.repository.FaqRepository;
import idu.sba.backend.domain.admin.repository.NoticeRepository;
import idu.sba.backend.domain.growth.entity.WeeklyReport;
import idu.sba.backend.domain.growth.repository.WeeklyReportRepository;
import idu.sba.backend.domain.inquiry.entity.Inquiry;
import idu.sba.backend.domain.inquiry.repository.InquiryRepository;
import idu.sba.backend.domain.learning.entity.AiSkill;
import idu.sba.backend.domain.learning.entity.Course;
import idu.sba.backend.domain.learning.entity.LearningCard;
import idu.sba.backend.domain.learning.entity.LearningCardQuiz;
import idu.sba.backend.domain.learning.entity.QuizSubmission;
import idu.sba.backend.domain.learning.repository.AiSkillRepository;
import idu.sba.backend.domain.learning.repository.CourseRepository;
import idu.sba.backend.domain.learning.repository.LearningCardQuizRepository;
import idu.sba.backend.domain.learning.repository.LearningCardRepository;
import idu.sba.backend.domain.learning.repository.QuizSubmissionRepository;
import idu.sba.backend.domain.notification.entity.Notification;
import idu.sba.backend.domain.notification.repository.NotificationRepository;
import idu.sba.backend.domain.payment.entity.ChangeType;
import idu.sba.backend.domain.payment.entity.CreditUsage;
import idu.sba.backend.domain.payment.entity.PaymentMethod;
import idu.sba.backend.domain.payment.entity.Subscription;
import idu.sba.backend.domain.payment.entity.SubscriptionHistory;
import idu.sba.backend.domain.payment.repository.CreditUsageRepository;
import idu.sba.backend.domain.payment.repository.PaymentMethodRepository;
import idu.sba.backend.domain.payment.repository.PlanRepository;
import idu.sba.backend.domain.payment.repository.SubscriptionHistoryRepository;
import idu.sba.backend.domain.payment.repository.SubscriptionRepository;
import idu.sba.backend.domain.pr.entity.PullRequest;
import idu.sba.backend.domain.pr.repository.PullRequestRepository;
import idu.sba.backend.domain.repo.entity.GithubRepository;
import idu.sba.backend.domain.repo.entity.RepoMember;
import idu.sba.backend.domain.repo.entity.RepoRole;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.repo.repository.RepoMemberRepository;
import idu.sba.backend.domain.retention.entity.PetState;
import idu.sba.backend.domain.retention.entity.UserStreak;
import idu.sba.backend.domain.retention.repository.PetStateRepository;
import idu.sba.backend.domain.retention.repository.UserStreakRepository;
import idu.sba.backend.domain.review.entity.AiUsageLog;
import idu.sba.backend.domain.review.entity.IssueCategory;
import idu.sba.backend.domain.review.entity.IssueSeverity;
import idu.sba.backend.domain.review.entity.IssueStatus;
import idu.sba.backend.domain.review.entity.Review;
import idu.sba.backend.domain.review.entity.ReviewIssue;
import idu.sba.backend.domain.review.repository.AiUsageLogRepository;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewRepository;
import idu.sba.backend.domain.terms.entity.UserAgreement;
import idu.sba.backend.domain.terms.repository.UserAgreementRepository;
import idu.sba.backend.domain.user.entity.Level;
import idu.sba.backend.domain.user.entity.Role;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/*
 * 개발용 시드 데이터. 빈 DB에 처음 뜰 때만 돌고, 이미 데이터가 있으면 아무것도 하지 않는다.
 * 사이트의 모든 화면을 로그인 직후 바로 눌러볼 수 있는 게 목표.
 *
 * 다시 심고 싶으면 DB를 비우고(DROP DATABASE cogi; CREATE DATABASE cogi;) 재시작하면 된다.
 *
 * 테스트 계정 — admin@a.a / 1234 (관리자), user@a.a / 1234 (일반, MAX 플랜)
 * 로그인 DTO에 @Email이 걸려 있어 아이디는 이메일 형태여야 한다.
 *
 * plans·terms는 엔티티에 생성 API가 없어(다른 팀원 코드라 손대지 않음) JdbcTemplate으로 넣는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String PW = "1234"; // 테스트 계정 공용 비밀번호

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final UserAgreementRepository userAgreementRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionHistoryRepository subscriptionHistoryRepository;
    private final CreditUsageRepository creditUsageRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final RepoMemberRepository repoMemberRepository;
    private final PullRequestRepository pullRequestRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewIssueRepository reviewIssueRepository;
    private final LearningCardRepository learningCardRepository;
    private final LearningCardQuizRepository learningCardQuizRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final UserStreakRepository userStreakRepository;
    private final WeeklyReportRepository weeklyReportRepository;
    private final CourseRepository courseRepository;
    private final AiSkillRepository aiSkillRepository;
    private final PetStateRepository petStateRepository;
    private final NoticeRepository noticeRepository;
    private final FaqRepository faqRepository;
    private final InquiryRepository inquiryRepository;
    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void run(String... args) {
        // 최초 1회만. 둘 중 하나라도 남아 있으면 이미 심은 것으로 본다
        // (plans는 id를 직접 박아 넣어서 두 번 돌면 중복키로 터진다)
        if (planRepository.count() > 0 || userRepository.count() > 0) {
            log.info("시드 데이터가 이미 있어 건너뜁니다.");
            return;
        }

        seedPlans();
        seedTerms();

        // 팀장 admin, 팀원 user·kim·lee. githubUsername을 채워야 팀·성장추이 화면이 이름을 제대로 띄운다
        // (연동 흐름을 직접 밟아볼 user@a.a만 예외로 비워 둔다).
        // admin만 비워 둔다 — 실제 GitHub 계정을 직접 연동해 봐야 해서 미연동 상태로 시작한다
        User admin = saveUser("admin@a.a", "관리자", null, Role.ADMIN, Level.ADVANCED, 3L);
        // user@a.a는 GitHub을 비워 둔다 — 마이페이지에서 직접 연동하는 흐름을 이 계정으로 확인한다.
        // PR 시드의 작성자 로그인("cogi-user")은 별개 문자열이라 여기 null이어도 그대로 뜬다
        User me = saveUser("user@a.a", "코기유저", null, Role.USER, Level.INTERMEDIATE, 3L);
        User kim = saveUser("kim@a.a", "김코기", "kimcogi", Role.USER, Level.BEGINNER, 1L);
        User lee = saveUser("lee@a.a", "이코기", "leecogi", Role.USER, Level.ADVANCED, 1L);

        seedAgreements(me);
        seedBilling(me);
        seedRepoAndPr(admin, me, kim, lee);
        seedWeakness(me);
        seedLearning(me);
        seedGrowth(me);
        seedCourses();
        seedSkills();
        seedPets(admin, me, kim, lee);
        seedSupport(me, admin);

        log.info("시드 완료 — admin@a.a(팀장) / user@a.a(팀원) 비밀번호 1234");
    }

    // ── 요금제. allowed_models 값은 AiModel enum의 id와 정확히 같아야 카드·퀴즈·리뷰가 모델을 찾는다 ──
    private void seedPlans() {
        String free = "claude-haiku-4-5,gpt-5.6-luna,gemini-3.5-flash";
        String pro = free + ",claude-sonnet-5,gpt-5.6-terra";
        String max = pro + ",claude-opus-4-8,gpt-5.6-sol";
        jdbc.update("INSERT INTO plans (id,name,daily_credit_limit,allowed_models,price) VALUES (1,'FREE',20,?,0)", free);
        jdbc.update("INSERT INTO plans (id,name,daily_credit_limit,allowed_models,price) VALUES (2,'PRO',40,?,30000)", pro);
        jdbc.update("INSERT INTO plans (id,name,daily_credit_limit,allowed_models,price) VALUES (3,'MAX',70,?,50000)", max);
    }

    // ── 약관. 가입·온보딩 화면이 이 목록을 그대로 띄운다 ──
    private void seedTerms() {
        insertTerm("SERVICE", "이용약관", "코기 서비스 이용에 관한 약관 본문입니다.", true);
        insertTerm("PRIVACY", "개인정보처리방침", "수집 항목과 보관 기간에 관한 본문입니다.", true);
        insertTerm("PAYMENT", "결제이용약관", "구독 결제와 환불에 관한 본문입니다.", false);
        insertTerm("MARKETING", "마케팅수신동의", "혜택 알림 수신에 관한 본문입니다.", false);
    }

    private void insertTerm(String type, String title, String content, boolean required) {
        jdbc.update("INSERT INTO terms (type,version,title,content,is_required,effective_date) VALUES (?,'1.0',?,?,?,'2026-01-01')",
                type, title, content, required);
    }

    // ── 계정. 온보딩까지 끝낸 상태로 만들어 로그인 직후 바로 모든 메뉴가 열리게 한다 ──
    private User saveUser(String email, String nickname, String githubUsername,
                          Role role, Level level, Long planId) {
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(PW))
                .nickname(nickname)
                .build();
        user.changeRole(role);
        user.updatePlanId(planId);
        user.completeOnboarding(level, "Java,Spring,React", true); // 온보딩 미완료면 첫 화면이 막힌다
        // GitHub에 연동된 것처럼 아이디만 채운다. 토큰은 가짜를 넣어도 GitHub이 401을 주므로 비워 둔다.
        // null이면 미연동 상태 — 화면에 GitHub 연동 버튼이 그대로 노출된다
        if (githubUsername != null) {
            user.linkGithub(githubUsername, githubUsername, null);
        }
        return userRepository.save(user);
    }

    // 필수 약관 2종에 동의한 이력 (마이페이지 약관 동의 내역)
    // 버전은 insertTerm이 넣는 '1.0'과 맞춘다. 어긋나면 로그인하자마자 재동의 배너가 뜬다 (FR-91)
    private void seedAgreements(User user) {
        userAgreementRepository.save(UserAgreement.of(user.getId(), 1L, "1.0"));
        userAgreementRepository.save(UserAgreement.of(user.getId(), 2L, "1.0"));
    }

    // ── 결제수단 → 구독(MAX) → 변경 이력 3건 + 오늘 크레딧 사용량 ──
    private void seedBilling(User user) {
        PaymentMethod pm = paymentMethodRepository.save(
                new PaymentMethod(user.getId(), "cust_test_1", "bill_test_1", "1234-****-****-5678"));

        Subscription sub = new Subscription();
        sub.start(user.getId(), 3L, pm.getId());
        subscriptionRepository.save(sub);

        // 요금제 페이지의 "변경 / 이전 / 이후 / 차액 / 일시" 표를 채운다
        saveHistory(sub.getId(), null, 1L, ChangeType.UPGRADE, 0);
        saveHistory(sub.getId(), 1L, 2L, ChangeType.UPGRADE, 30000);
        saveHistory(sub.getId(), 2L, 3L, ChangeType.UPGRADE, 20000);

        CreditUsage usage = new CreditUsage();
        usage.start(user.getId(), LocalDate.now(), 70); // MAX 한도
        usage.consume(5); // 오늘 5개 쓴 상태로 보여준다
        creditUsageRepository.save(usage);
    }

    private void saveHistory(Long subId, Long from, Long to, ChangeType type, int amount) {
        SubscriptionHistory h = new SubscriptionHistory();
        h.record(subId, from, to, type, amount);
        subscriptionHistoryRepository.save(h);
    }

    // ── 레포 2개 + 팀원 + PR 3건과 그 리뷰 결과 (팀 페이지 / PR 대시보드) ──
    // 레포 주인은 admin이다. 팀장 화면(위임·내보내기)을 admin으로 로그인해 확인할 수 있어야 해서다
    private void seedRepoAndPr(User admin, User me, User kim, User lee) {
        GithubRepository repo1 = githubRepositoryRepository.save(
                GithubRepository.link(admin.getId(), "900001", "cogi-backend", false, "cogi/cogi-backend"));
        GithubRepository repo2 = githubRepositoryRepository.save(
                GithubRepository.link(admin.getId(), "900002", "cogi-frontend", false, "cogi/cogi-frontend"));

        repoMemberRepository.saveAll(List.of(
                RepoMember.of(repo1.getId(), admin.getId(), RepoRole.OWNER),
                RepoMember.of(repo1.getId(), me.getId(), RepoRole.MEMBER),
                RepoMember.of(repo1.getId(), kim.getId(), RepoRole.MEMBER),
                RepoMember.of(repo1.getId(), lee.getId(), RepoRole.MEMBER),
                RepoMember.of(repo2.getId(), admin.getId(), RepoRole.OWNER),
                RepoMember.of(repo2.getId(), me.getId(), RepoRole.MEMBER)));

        // 지적 없이 통과한 PR — 화면에서 "해결 2 / 미해결 0" 상태를 확인하는 용도
        prWithIssues(repo1.getId(), 10, "refactor: 주문 조회 쿼리 정리", me, "cogi-user", "claude-sonnet-5", "Java", 2, List.of(
                issue(IssueCategory.CODE_SMELL, IssueSeverity.MINOR, "OrderService.java", 68,
                        "내보내기 조건이 두 곳에 흩어져 있습니다. 한쪽만 고치면 나머지가 남아 어긋납니다. 한 군데로 모으는 편이 안전합니다:\n"
                                + "```java\nprivate boolean exportable(Order o) {   // 판정을 한곳에 모음\n    return o.getSnippet() != null && !o.getSnippet().isEmpty();\n}\n```\n"
                                + "호출하는 쪽은 `exportable(order)` 하나만 보게 됩니다.", IssueStatus.RESOLVED),
                issue(IssueCategory.CONVENTION, IssueSeverity.MINOR, "OrderService.java", 24,
                        "상수가 소문자로 선언돼 있습니다. 자바 관례상 `static final` 은 대문자와 밑줄로 씁니다:\n"
                                + "```java\nprivate static final int MAX_PAGE_SIZE = 100;   // maxPageSize → MAX_PAGE_SIZE\n```",
                        IssueStatus.RESOLVED)));

        // 지적이 남아 있는 PR — 심각/보안 이슈가 미해결로 뜨는 상태
        prWithIssues(repo1.getId(), 11, "feat: 사용자 조회 API 추가", kim, "kimcogi", "claude-haiku-4-5", "Java", 9, List.of(
                issue(IssueCategory.SECURITY, IssueSeverity.CRITICAL, "VulnerableLookupExample.java", 15,
                        "SQL 인젝션 위험입니다. 사용자 입력 `userName` 이 검증이나 파라미터화 없이 쿼리에 직접 붙고 있습니다. "
                                + "`' OR '1'='1` 같은 값을 넣으면 조건이 통째로 무력화됩니다. PreparedStatement로 바인딩하세요:\n"
                                + "```java\nString query = \"SELECT * FROM users WHERE nickname = ?\";   // 파라미터 자리\nPreparedStatement pstmt = conn.prepareStatement(query);\npstmt.setString(1, userName);   // 값으로만 전달\nreturn pstmt.executeQuery();\n```\n"
                                + "쿼리 구조와 데이터가 분리되어 인젝션이 원천 차단됩니다.", IssueStatus.OPEN),
                issue(IssueCategory.SECURITY, IssueSeverity.CRITICAL, "VulnerableLookupExample.java", 11,
                        "DB 비밀번호가 소스에 평문으로 들어 있습니다. 커밋되면 저장소 접근 권한이 있는 모두와 히스토리에 그대로 남습니다. "
                                + "환경 변수나 설정 관리 도구로 옮기세요:\n"
                                + "```java\nprivate static final String DB_PASSWORD = System.getenv(\"DB_PASSWORD\");\n```\n"
                                + "Spring이면 `@Value` 로 외부 설정에서 주입받아도 됩니다.", IssueStatus.OPEN),
                issue(IssueCategory.BUG, IssueSeverity.MAJOR, "UserLookup.java", 30,
                        "빈 결과일 때 `get(0)` 을 호출해 인덱스 예외가 납니다. 비어 있는지 먼저 보세요:\n"
                                + "```java\nreturn rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));\n```",
                        IssueStatus.RESOLVED)));

        prWithIssues(repo1.getId(), 12, "fix: 캐시 만료 처리 보완", lee, "leecogi", "claude-haiku-4-5", "Python", 16, List.of(
                issue(IssueCategory.PERFORMANCE, IssueSeverity.MAJOR, "cache.py", 42,
                        "반복문 안에서 매번 조회하고 있어 항목 수만큼 쿼리가 나갑니다. 한 번에 가져와 사전으로 바꿔 쓰세요:\n"
                                + "```python\nrows = repo.find_all(ids)           # 한 번만 조회\nby_id = {r.id: r for r in rows}\nresult = [by_id[i] for i in ids if i in by_id]\n```",
                        IssueStatus.OPEN),
                issue(IssueCategory.CONVENTION, IssueSeverity.MINOR, "cache.py", 12,
                        "함수 이름이 동사로 시작하지 않아 무엇을 하는지 읽히지 않습니다. `cache_data` 보다 `load_cache` 쪽이 의도가 드러납니다.",
                        IssueStatus.RESOLVED)));

        // 이번 주에도 해결 건을 남겨 성장추이의 해결 곡선이 바닥에 깔리지 않게 한다
        prWithIssues(repo1.getId(), 13, "fix: 널 반환 정리", me, "cogi-user", "claude-haiku-4-5", "Java", 1, List.of(
                issue(IssueCategory.BUG, IssueSeverity.MAJOR, "UserService.java", 22,
                        "널 대신 `Optional`을 돌려주도록 고쳤습니다. 호출한 쪽이 값 없음을 반드시 다루게 됩니다.",
                        IssueStatus.RESOLVED),
                issue(IssueCategory.CODE_SMELL, IssueSeverity.MINOR, "UserService.java", 40,
                        "중복된 조회 로직을 한 메서드로 모았습니다.", IssueStatus.RESOLVED)));
    }

    // PR 한 건 + 완료된 리뷰 + 이슈들. daysAgo만큼 과거로 밀어야 성장추이 그래프에 주별로 흩어진다
    private void prWithIssues(Long repoId, int prNumber, String title, User author, String login,
                              String model, String language, int daysAgo, List<ReviewIssue> issues) {
        PullRequest pr = PullRequest.open(repoId, prNumber, title, author.getId(), login);
        pr.selectModel(model);
        pr.markReviewed();
        pullRequestRepository.save(pr);

        Review review = Review.createFromPr(author.getId(), pr.getId(), model);
        review.markCompleted();
        reviewRepository.save(review);
        backdate("reviews", review.getId(), daysAgo);

        issues.forEach(i -> {
            setReviewId(i, review.getId());
            reviewIssueRepository.save(i);
            backdate("review_issues", i.getId(), daysAgo);
        });
    }

    // ── 약점 통계 시드 (LRN-001). 같은 카테고리 3회 이상이어야 약점으로 올라온다 (FR-56) ──
    private void seedWeakness(User me) {
        // 붙여넣기 리뷰 = Java. BUG 3회로 약점 성립, IGNORED 1건은 acknowledged라 집계에서 빠진다
        Review java = reviewRepository.save(Review.createPaste(me.getId(), "// 시드 코드", "Java", "claude-haiku-4-5"));
        java.markCompleted();
        saveIssues(java.getId(), List.of(
                issue(IssueCategory.BUG, IssueSeverity.MAJOR, "Foo.java", 10, "널 반환을 그대로 흘려보냄", IssueStatus.OPEN),
                issue(IssueCategory.BUG, IssueSeverity.MAJOR, "Foo.java", 20, "예외를 삼키고 넘어감", IssueStatus.OPEN),
                issue(IssueCategory.BUG, IssueSeverity.MAJOR, "Foo.java", 30, "경계 조건 누락", IssueStatus.OPEN),
                issue(IssueCategory.CONVENTION, IssueSeverity.MINOR, "Foo.java", 50, "메서드명이 동사가 아님", IssueStatus.OPEN),
                issue(IssueCategory.CONVENTION, IssueSeverity.MINOR, "Foo.java", 60, "상수를 대문자로 쓰지 않음", IssueStatus.OPEN)));

        // 의도한 코드로 표시한 건 — 약점 통계에서 제외되는지 확인용
        ReviewIssue ignored = issue(IssueCategory.BUG, IssueSeverity.MAJOR, "Foo.java", 40, "의도한 널 반환", IssueStatus.OPEN);
        setReviewId(ignored, java.getId());
        ignored.finalizeAs(IssueStatus.IGNORED);
        reviewIssueRepository.save(ignored);

        // 업로드 리뷰 = Python. 언어가 다르면 따로 집계된다 (FR-65)
        Review python = reviewRepository.save(
                Review.createUpload(me.getId(), "# 시드 코드", "Python", "bar.py", "claude-haiku-4-5"));
        python.markCompleted();
        saveIssues(python.getId(), List.of(
                issue(IssueCategory.BUG, IssueSeverity.MAJOR, "bar.py", 5, "가변 기본 인자 사용", IssueStatus.OPEN),
                issue(IssueCategory.BUG, IssueSeverity.MAJOR, "bar.py", 15, "except로 전부 잡아버림", IssueStatus.OPEN),
                issue(IssueCategory.BUG, IssueSeverity.MAJOR, "bar.py", 25, "파일을 닫지 않음", IssueStatus.OPEN)));

        // ai_usage_logs는 일부러 비워 둔다. 실제 AI를 호출할 때만 쌓여야 토큰·비용 화면이 진짜 값을 보여준다
    }

    private void saveIssues(Long reviewId, List<ReviewIssue> issues) {
        issues.forEach(i -> {
            setReviewId(i, reviewId);
            reviewIssueRepository.save(i);
        });
    }

    // ── 학습카드 + 푼 문제 + 연속 학습일 (LRN-002 / RET-001) ──
    private void seedLearning(User me) {
        LearningCard card = learningCardRepository.save(LearningCard.create(
                me.getId(), "BUG", "INTERMEDIATE", "Java", "claude-haiku-4-5",
                "널 반환은 호출한 쪽이 확인을 잊는 순간 그대로 터진다. 값이 없을 수 있다는 사실을 타입에 담아 넘기면 컴파일 단계에서 걸린다.",
                "널을 흘려보내면 터지는 지점이 반환한 곳에서 멀어진다. 로그를 봐도 원인을 찾기 어렵다.",
                "public User find(Long id) {\n    return repo.findById(id).orElse(null);\n}",
                "public Optional<User> find(Long id) {\n    return repo.findById(id);\n}",
                "널 대신 Optional을 돌려주면 호출한 쪽이 값 없음을 다루게 강제된다.",
                "널을 돌려주지 말고 Optional로 감싼다\nOptional은 반환 타입에만 쓴다\n값이 없는 게 예외 상황이면 예외를 던진다",
                "Optional을 필드나 파라미터에 쓴다\nisPresent + get으로 결국 널 체크를 반복한다\norElse에 비싼 연산을 넣는다",
                "값이 없을 수 있는 메서드가 널을 돌려주고 있지 않은가?\nOptional을 필드에 쓰지 않았는가?\norElseGet을 써야 할 자리에 orElse를 쓰지 않았는가?"));

        // 지난 문제 복습 화면을 채우려면 푼 문제가 여러 개 있어야 한다. 정답·오답을 섞는다
        LearningCardQuiz q1 = saveQuiz(card.getId(), "MULTIPLE_CHOICE",
                "값이 없을 수 있는 조회 메서드의 반환 타입으로 가장 적절한 것은?",
                "null\nOptional<User>\nUser\nList<User>", "Optional<User>",
                "널을 돌려주면 호출한 쪽이 확인을 잊어도 컴파일러가 못 잡는다. Optional은 값 없음을 타입에 담아 강제하므로 확인을 건너뛸 수 없다.");
        LearningCardQuiz q2 = saveQuiz(card.getId(), "OX",
                "Optional은 엔티티 필드 타입으로 쓰는 것이 권장된다.",
                "O\nX", "X",
                "Optional은 반환 타입으로 쓰라고 만들어졌다. 필드에 두면 직렬화가 까다로워지고 값이 없는 상태가 두 겹으로 생긴다.");
        LearningCardQuiz q3 = saveQuiz(card.getId(), "SHORT_ANSWER",
                "기본값 계산이 비쌀 때 orElse 대신 써야 하는 메서드 이름은?",
                null, "orElseGet",
                "orElse는 값이 있든 없든 인자를 먼저 평가한다. orElseGet은 비어 있을 때만 람다를 실행해 불필요한 연산을 건너뛴다.");

        // 정답 2 + 오답 1. gradeAfter는 그 시점 등급이라 정답이 쌓이는 순서대로 넣는다
        card.recordCorrect();
        quizSubmissionRepository.save(QuizSubmission.create(q1.getId(), me.getId(), "Optional<User>", true, card.getGrade()));
        card.recordCorrect();
        quizSubmissionRepository.save(QuizSubmission.create(q2.getId(), me.getId(), "X", true, card.getGrade()));
        quizSubmissionRepository.save(QuizSubmission.create(q3.getId(), me.getId(), "orElse", false, card.getGrade()));

        // 사흘 연속 제출한 상태로 만든다 — 하루씩 이어 붙여야 streak가 올라간다
        UserStreak streak = UserStreak.create(me.getId());
        streak.recordSubmission(LocalDate.now().minusDays(2));
        streak.recordSubmission(LocalDate.now().minusDays(1));
        streak.recordSubmission(LocalDate.now());
        userStreakRepository.save(streak);
    }

    // 퀴즈 한 문제 저장 — 보기가 없는 유형(주관식·빈칸)은 options에 null을 넣는다
    private LearningCardQuiz saveQuiz(Long cardId, String type, String question,
                                      String options, String answer, String explain) {
        return learningCardQuizRepository.save(
                LearningCardQuiz.create(cardId, type, "claude-haiku-4-5", question, options, answer, explain));
    }

    // ── AI별 추천 스킬 (LRN-005). 화면에서 고른 AI에 맞는 것만 걸러 보여준다 ──
    private void seedSkills() {
        aiSkillRepository.saveAll(List.of(
                AiSkill.of("CLAUDE", "BUG", "확장 사고(Extended Thinking)",
                        "답을 내기 전에 근거를 길게 따져보게 하는 기능. 원인이 여러 겹인 버그에서 헛다리를 덜 짚는다.",
                        "복잡한 버그를 물을 때 \"단계별로 근거를 따져본 뒤 결론을 내줘\"를 덧붙인다.",
                        "https://docs.anthropic.com/en/docs/build-with-claude/extended-thinking"),
                AiSkill.of("CLAUDE", "SECURITY", "프로젝트 지식(Projects)",
                        "보안 규칙 문서를 프로젝트에 올려두면 매번 붙여넣지 않아도 그 기준으로 검토해 준다.",
                        "사내 보안 가이드를 Project Knowledge에 올리고 \"이 기준으로 검토해줘\"라고 요청한다.",
                        "https://www.anthropic.com/news/projects"),
                AiSkill.of("CLAUDE", "CODE_SMELL", "Claude Code 리팩터링",
                        "터미널에서 저장소를 통째로 읽고 고쳐준다. 중복 코드처럼 여러 파일에 흩어진 문제에 강하다.",
                        "`claude` 를 실행하고 \"중복된 로직을 찾아 한 곳으로 모아줘\"라고 지시한다.",
                        "https://docs.anthropic.com/en/docs/claude-code/overview"),
                AiSkill.of("CHATGPT", "BUG", "코드 인터프리터",
                        "코드를 실제로 돌려보고 결과를 확인한다. 재현되는 버그를 빠르게 좁힌다.",
                        "실패하는 입력과 함께 코드를 올리고 \"직접 실행해서 어디서 갈리는지 찾아줘\"라고 한다.",
                        "https://help.openai.com/en/articles/8437071"),
                AiSkill.of("CHATGPT", "PERFORMANCE", "Custom Instructions",
                        "매번 반복하는 요구사항을 미리 등록해 둔다. 성능 기준을 항상 같이 보게 만든다.",
                        "설정에 \"성능 지적은 항상 시간복잡도와 함께 설명\"을 넣어 둔다.",
                        "https://openai.com/index/custom-instructions-for-chatgpt/"),
                AiSkill.of("CHATGPT", "CONVENTION", "Projects로 컨벤션 고정",
                        "팀 컨벤션 문서를 프로젝트에 넣어두면 그 규칙으로만 지적한다.",
                        "스타일 가이드를 올리고 \"이 규칙에서 벗어난 곳만 알려줘\"라고 요청한다.", null),
                AiSkill.of("GEMINI", "PERFORMANCE", "긴 컨텍스트 분석",
                        "한 번에 아주 많은 코드를 넣을 수 있다. 파일을 넘나드는 성능 문제를 통으로 본다.",
                        "관련 파일을 한꺼번에 올리고 \"호출 경로를 따라가며 병목을 짚어줘\"라고 한다.",
                        "https://ai.google.dev/gemini-api/docs/long-context"),
                AiSkill.of("GEMINI", "BUG", "Grounding with Search",
                        "라이브러리 최신 이슈를 검색해 근거로 삼는다. 버전 탓에 생긴 버그에 유용하다.",
                        "라이브러리와 버전을 밝히고 \"알려진 이슈가 있는지 확인해줘\"라고 요청한다.", null),
                AiSkill.of("COPILOT", "CONVENTION", "커스텀 인스트럭션 파일",
                        "저장소에 규칙 파일을 두면 제안이 그 컨벤션을 따라간다.",
                        "`.github/copilot-instructions.md` 에 네이밍·포맷 규칙을 적어 둔다.",
                        "https://docs.github.com/en/copilot/customizing-copilot"),
                AiSkill.of("COPILOT", "CODE_SMELL", "Copilot Chat /fix",
                        "선택한 코드의 문제를 바로 고쳐 준다. 자잘한 냄새를 즉석에서 걷어낸다.",
                        "고칠 부분을 선택하고 채팅창에 `/fix` 를 입력한다.", null),
                AiSkill.of("COPILOT", "SECURITY", "코드 스캐닝 자동 수정",
                        "취약점을 찾고 수정안을 PR로 올려 준다.",
                        "저장소 Settings에서 Code scanning을 켜고 autofix를 허용한다.",
                        "https://docs.github.com/en/code-security")));
    }

    // ── 다마고치. 계정마다 따로 자라야 해서 각자 한 행씩 만든다 ──
    private void seedPets(User admin, User me, User kim, User lee) {
        // user@a.a는 어느 정도 키워둔 상태로 보여준다. 나머지는 갓 시작한 코기
        PetState grown = pet(me.getId());
        grown.update(120, 72, 66, 81, 210, null);

        List.of(admin, kim, lee).forEach(u -> pet(u.getId()));
    }

    // 앱이 켜져 있는 채로 시드를 돌리면 화면이 먼저 코기를 만들어 둘 수 있다. 있으면 그걸 쓴다
    private PetState pet(Long userId) {
        return petStateRepository.findByUserId(userId)
                .orElseGet(() -> petStateRepository.save(PetState.create(userId)));
    }

    // ── 주간 리포트 2주치 (RET-002 / 성장추이). 이슈가 줄어드는 흐름으로 넣는다 ──
    private void seedGrowth(User me) {
        LocalDate thisMonday = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        weeklyReportRepository.save(WeeklyReport.of(me.getId(),
                thisMonday.minusWeeks(1), thisMonday.minusDays(1), 8, 5, 12, "BUG",
                "지난주 이슈 8건 · 전주 12건 대비 4건 줄었고 5건을 해결했습니다. 최다 약점은 BUG입니다."));
        weeklyReportRepository.save(WeeklyReport.of(me.getId(),
                thisMonday.minusWeeks(2), thisMonday.minusWeeks(1).minusDays(1), 12, 3, 10, "SECURITY",
                "2주 전 이슈 12건 중 3건을 해결했습니다. 최다 약점은 SECURITY입니다."));
    }

    // ── 강의 추천 (LRN-003). 약점 category와 맞물려야 화면에 뜬다 ──
    private void seedCourses() {
        courseRepository.saveAll(List.of(
                // ── 언어별 강의 확충 (BUG / PERFORMANCE / CODE_SMELL / CONVENTION / SECURITY × Java·Python·JS·TS·C++ + 언어무관) ──
                Course.of("BUG", "Java", "UDEMY", "Java Programming Masterclass",
                        "자바 예외 처리·디버깅까지. 반복 버그의 근본 원인을 잡는 정석.", "https://www.udemy.com/course/java-the-complete-java-developer-course/"),
                Course.of("BUG", "Python", "UDEMY", "Complete Python Bootcamp",
                        "파이썬 예외·타입 오류 등 자주 나는 버그를 기초부터 잡는다.", "https://www.udemy.com/course/complete-python-bootcamp/"),
                Course.of("BUG", "JavaScript", "UDEMY", "The Complete JavaScript Course",
                        "null/undefined·비동기 등 JS 버그 다발 지점을 집중적으로 다룬다.", "https://www.udemy.com/course/the-complete-javascript-course/"),
                Course.of("BUG", "TypeScript", "UDEMY", "Understanding TypeScript",
                        "타입으로 런타임 버그를 컴파일 단계에서 잡는 법.", "https://www.udemy.com/course/understanding-typescript/"),
                Course.of("BUG", "C++", "UDEMY", "Beginning C++ Programming",
                        "포인터·메모리 등 C++ 버그의 온상을 기초부터 다진다.", "https://www.udemy.com/course/beginning-c-plus-plus-programming/"),
                Course.of("BUG", null, "INFLEARN", "실전 디버깅과 예외 처리",
                        "언어 무관 디버깅 사고법과 예외 처리 원칙.", "https://www.inflearn.com/ko/courses?s=디버깅"),
                Course.of("PERFORMANCE", "Java", "UDEMY", "Java Multithreading, Concurrency & Performance",
                        "멀티스레딩·동시성으로 병목 제거. N+1·불필요 연산 개선.", "https://www.udemy.com/course/java-multithreading-concurrency-performance-optimization/"),
                Course.of("PERFORMANCE", "Python", "UDEMY", "Python 성능 최적화",
                        "파이썬 느린 코드 프로파일링·최적화 실전.", "https://www.udemy.com/courses/search/?q=Python%20성능%20최적화&lang=ko"),
                Course.of("PERFORMANCE", "JavaScript", "UDEMY", "JavaScript 성능 최적화",
                        "렌더링·메모리·비동기 병목을 줄이는 JS 성능 기법.", "https://www.udemy.com/courses/search/?q=JavaScript%20performance&lang=ko"),
                Course.of("PERFORMANCE", "TypeScript", "INFLEARN", "TypeScript 성능·최적화",
                        "대규모 TS 프로젝트의 빌드·런타임 성능 개선.", "https://www.inflearn.com/ko/courses?s=타입스크립트%20성능"),
                Course.of("PERFORMANCE", "C++", "UDEMY", "C++ 고성능 프로그래밍",
                        "캐시·메모리·알고리즘 관점의 C++ 성능 최적화.", "https://www.udemy.com/courses/search/?q=C%2B%2B%20performance&lang=ko"),
                Course.of("PERFORMANCE", null, "UDEMY", "The Complete SQL Bootcamp",
                        "느린 쿼리·인덱스 미사용 등 DB 성능을 다진다(언어무관).", "https://www.udemy.com/course/the-complete-sql-bootcamp/"),
                Course.of("CODE_SMELL", "Java", "INFLEARN", "자바 클린코드와 리팩터링",
                        "자바 관점의 중복·긴 함수·나쁜 설계 리팩터링.", "https://www.inflearn.com/ko/courses?s=자바%20클린코드"),
                Course.of("CODE_SMELL", "Python", "INFLEARN", "파이썬 클린코드",
                        "파이썬다운 코드로 냄새를 제거하는 법.", "https://www.inflearn.com/ko/courses?s=파이썬%20클린코드"),
                Course.of("CODE_SMELL", "JavaScript", "UDEMY", "JavaScript Clean Code",
                        "JS 코드 냄새와 리팩터링 패턴.", "https://www.udemy.com/courses/search/?q=JavaScript%20clean%20code&lang=ko"),
                Course.of("CODE_SMELL", "TypeScript", "UDEMY", "TypeScript Clean Code",
                        "타입 안전한 클린코드와 리팩터링.", "https://www.udemy.com/courses/search/?q=TypeScript%20clean%20code&lang=ko"),
                Course.of("CODE_SMELL", "C++", "UDEMY", "Modern C++ Clean Code",
                        "모던 C++로 나쁜 코드를 개선하는 법.", "https://www.udemy.com/courses/search/?q=modern%20C%2B%2B&lang=ko"),
                Course.of("CODE_SMELL", null, "UDEMY", "Clean Code",
                        "중복·긴 함수·매직넘버 등 코드 냄새 제거(언어무관).", "https://www.udemy.com/course/writing-clean-code/"),
                Course.of("CONVENTION", "Java", "INFLEARN", "자바 코딩 컨벤션",
                        "네이밍·포매팅 등 자바 팀 컨벤션.", "https://www.inflearn.com/ko/courses?s=자바%20코딩%20컨벤션"),
                Course.of("CONVENTION", "Python", "INFLEARN", "파이썬 PEP8 스타일",
                        "PEP8 등 파이썬 코드 스타일 규칙.", "https://www.inflearn.com/ko/courses?s=파이썬%20PEP8"),
                Course.of("CONVENTION", "JavaScript", "UDEMY", "JavaScript 코딩 스타일",
                        "ESLint·표준 스타일로 일관성 잡기.", "https://www.udemy.com/courses/search/?q=JavaScript%20eslint%20style&lang=ko"),
                Course.of("CONVENTION", "TypeScript", "UDEMY", "TypeScript 스타일 가이드",
                        "타입 규칙과 린트로 컨벤션 통일.", "https://www.udemy.com/courses/search/?q=TypeScript%20lint&lang=ko"),
                Course.of("CONVENTION", "C++", "UDEMY", "C++ 코딩 스타일",
                        "C++ 네이밍·헤더·포매팅 관례.", "https://www.udemy.com/courses/search/?q=C%2B%2B%20coding%20style&lang=ko"),
                Course.of("CONVENTION", null, "UDEMY", "SOLID Principles",
                        "일관된 설계 규칙으로 컨벤션 위반을 구조적으로 줄인다(언어무관).", "https://www.udemy.com/course/solid-principles-object-oriented-design/"),
                Course.of("SECURITY", "Java", "INFLEARN", "자바·스프링 웹 보안",
                        "스프링 시큐리티·인증/인가 등 자바 웹 보안.", "https://www.inflearn.com/ko/courses?s=스프링%20시큐리티"),
                Course.of("SECURITY", "Python", "UDEMY", "Python 보안",
                        "파이썬 웹/스크립트의 취약점과 방어.", "https://www.udemy.com/courses/search/?q=Python%20security&lang=ko"),
                Course.of("SECURITY", "JavaScript", "UDEMY", "JavaScript 웹 보안",
                        "XSS·CSRF 등 프론트/노드 취약점 방어.", "https://www.udemy.com/courses/search/?q=JavaScript%20security&lang=ko"),
                Course.of("SECURITY", "TypeScript", "UDEMY", "Node/TypeScript 보안",
                        "타입 안전한 백엔드의 보안 실무.", "https://www.udemy.com/courses/search/?q=Node%20security&lang=ko"),
                Course.of("SECURITY", "C++", "UDEMY", "C++ 시큐어 코딩",
                        "버퍼 오버플로우 등 C++ 메모리 보안.", "https://www.udemy.com/courses/search/?q=C%2B%2B%20secure%20coding&lang=ko"),
                Course.of("SECURITY", null, "UDEMY", "Web Security & Bug Bounty",
                        "SQL 인젝션·XSS 등 웹 취약점 공격/방어(언어무관).", "https://www.udemy.com/course/web-security-bug-bounty-learn-penetration-testing/")));
    }

    // ── 공지·FAQ·문의·알림 (관리자 화면과 고객센터) ──
    private void seedSupport(User me, User admin) {
        noticeRepository.save(Notice.create(admin.getId(), "코기 정식 오픈 안내",
                "학습카드와 주간 리포트가 열렸습니다. 마이페이지에서 확인해 주세요.", 4));
        noticeRepository.save(Notice.create(admin.getId(), "정기 점검 안내",
                "매주 일요일 새벽 2시부터 30분간 점검이 있습니다.", 4));

        faqRepository.saveAll(List.of(
                Faq.create("크레딧은 언제 초기화되나요?", "매일 자정(한국 시간)에 요금제 한도만큼 다시 채워집니다.", "요금제", true),
                Faq.create("약점은 어떻게 잡히나요?", "같은 카테고리 지적이 3번 쌓이면 약점 통계에 올라옵니다.", "학습", true),
                Faq.create("의도한 코드인데 약점으로 잡혔어요", "이슈에서 '인지함'을 누르면 그 건은 통계에서 빠집니다.", "학습", true),
                Faq.create("요금제를 바꾸면 언제 반영되나요?", "결제 즉시 반영되고 차액은 일할 계산됩니다.", "요금제", true)));

        inquiryRepository.save(Inquiry.create(me.getId(), me.getEmail(), me.getNickname(),
                "PR 리뷰가 안 돌아요", "웹훅을 붙였는데 리뷰 결과가 생기지 않습니다. 확인 부탁드립니다."));

        notificationRepository.saveAll(List.of(
                Notification.of(me.getId(), "🔴", "새 약점이 잡혔어요", "Java BUG가 3회 반복됐습니다.", "/app/weakness"),
                Notification.of(me.getId(), "📗", "학습카드가 준비됐어요", "BUG 카드로 퀴즈를 풀어보세요.", "/app/cards"),
                Notification.of(me.getId(), "📈", "주간 리포트 도착", "지난주 이슈가 4건 줄었습니다.", "/app/growth")));
    }

    // 이슈 하나 만들기 (reviewId는 리뷰를 저장한 뒤에 채운다)
    private ReviewIssue issue(IssueCategory category, IssueSeverity severity,
                              String filePath, int line, String description, IssueStatus status) {
        ReviewIssue i = ReviewIssue.of(null, category, severity, filePath, line, description);
        if (status == IssueStatus.RESOLVED) {
            i.finalizeAs(IssueStatus.RESOLVED); // 해결 처리된 건도 섞어야 해결률이 보인다
        }
        return i;
    }

    // ReviewIssue.of는 reviewId를 생성 시점에 받는데, 리뷰를 먼저 저장해야 id가 나온다.
    // 엔티티에 setter를 뚫는 대신(다른 팀원 코드) 시드에서만 리플렉션으로 채운다
    private void setReviewId(ReviewIssue issue, Long reviewId) {
        try {
            var field = ReviewIssue.class.getDeclaredField("reviewId");
            field.setAccessible(true);
            field.set(issue, reviewId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("시드 이슈에 reviewId를 넣지 못했습니다", e);
        }
    }

    // created_at은 @PrePersist가 현재 시각으로 박아서, 과거 데이터는 저장 후 되돌려야 한다.
    // 성장추이가 주 단위로 묶어 보여주려면 날짜가 흩어져 있어야 한다
    private void backdate(String table, Long id, int daysAgo) {
        if (daysAgo > 0) {
            jdbc.update("UPDATE " + table + " SET created_at = DATE_SUB(NOW(), INTERVAL ? DAY) WHERE id = ?", daysAgo, id);
        }
    }
}
