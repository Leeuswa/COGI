package idu.sba.backend.domain.demo;

import idu.sba.backend.domain.growth.entity.WeeklyReport;
import idu.sba.backend.domain.growth.repository.WeeklyReportRepository;
import idu.sba.backend.domain.learning.entity.AiSkillFavorite;
import idu.sba.backend.domain.learning.entity.AiSkillRecommendation;
import idu.sba.backend.domain.learning.entity.LearningCard;
import idu.sba.backend.domain.learning.entity.LearningCardQuiz;
import idu.sba.backend.domain.learning.entity.QuizSubmission;
import idu.sba.backend.domain.learning.repository.AiSkillFavoriteRepository;
import idu.sba.backend.domain.learning.repository.AiSkillRecommendationRepository;
import idu.sba.backend.domain.learning.repository.LearningCardQuizRepository;
import idu.sba.backend.domain.learning.repository.LearningCardRepository;
import idu.sba.backend.domain.learning.repository.QuizSubmissionRepository;
import idu.sba.backend.domain.notification.entity.Notification;
import idu.sba.backend.domain.notification.repository.NotificationRepository;
import idu.sba.backend.domain.payment.entity.ChangeType;
import idu.sba.backend.domain.payment.entity.CreditUsage;
import idu.sba.backend.domain.payment.entity.PaymentMethod;
import idu.sba.backend.domain.payment.entity.Plan;
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
import idu.sba.backend.domain.review.entity.IssueStatus;
import idu.sba.backend.domain.review.entity.Review;
import idu.sba.backend.domain.review.entity.ReviewIssue;
import idu.sba.backend.domain.review.entity.ReviewQuestion;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewQuestionRepository;
import idu.sba.backend.domain.review.repository.ReviewRepository;
import idu.sba.backend.domain.terms.entity.Term;
import idu.sba.backend.domain.terms.entity.UserAgreement;
import idu.sba.backend.domain.terms.repository.TermRepository;
import idu.sba.backend.domain.terms.repository.UserAgreementRepository;
import idu.sba.backend.domain.user.entity.Level;
import idu.sba.backend.domain.user.entity.Role;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.entity.UserStatus;
import idu.sba.backend.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/*
 * 테스트 계정 하나(와 그 계정의 가상 팀원)의 데이터를 지우고 처음 상태로 다시 심는다.
 * 지우는 범위는 그 계정과 팀원의 userId, 그리고 그들이 만든 예시 저장소에 걸린 행뿐이다. 다른 회원 행은 조건에 걸리지 않는다.
 *
 * 날짜는 "오늘" 기준으로 다시 잡는다. 엔티티는 저장할 때 created_at 을 지금 시각으로 박으므로,
 * 전부 저장한 뒤 영속성 컨텍스트를 비우고 JDBC 로 과거 시각을 덮어쓴다(DataInitializer 와 같은 방식).
 * 비우기 전에 덮어쓰면 뒤이은 엔티티 변경이 옛 값을 다시 써 버린다.
 *
 * ai_usage_logs 는 지우지 않는다. 테스트 계정이 실제로 AI 를 부른 비용 기록이라 관리자 사용량 화면에 남아야 한다.
 * 반드시 트랜잭션 안에서 부른다 (DemoLoginService 가 감싼다).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cogi.demo", name = "enabled", havingValue = "true")
public class DemoSeeder {

    private static final String INTERESTS = "Java,Spring Boot,JPA";
    private static final String REPO_OWNER = "cogi-demo"; // 예시 저장소 full_name 의 앞부분 (실제 GitHub 저장소 아님)

    @PersistenceContext
    private EntityManager em;

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final TermRepository termRepository;
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
    private final ReviewQuestionRepository reviewQuestionRepository;
    private final LearningCardRepository learningCardRepository;
    private final LearningCardQuizRepository learningCardQuizRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final UserStreakRepository userStreakRepository;
    private final WeeklyReportRepository weeklyReportRepository;
    private final PetStateRepository petStateRepository;
    private final NotificationRepository notificationRepository;
    private final AiSkillRecommendationRepository aiSkillRecommendationRepository;
    private final AiSkillFavoriteRepository aiSkillFavoriteRepository;

    // 한 번 심는 동안만 쓰는 상태
    private record Backdate(String table, String column, Long id, LocalDateTime at) {}

    private record SeededPr(PullRequest pr, Long authorId, Long latestReviewId, LocalDateTime reviewedAt) {}

    /** 계정과 팀원을 (없으면 만들고) 잠근 뒤 지우고 다시 심는다. 로그인 계정의 userId 를 돌려준다. */
    public User resetAndSeed(DemoKind kind) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        List<Backdate> later = new ArrayList<>();

        User account = ensureUser(kind.key(), kind.nickname(), kind.githubUsername(), kind.role());
        List<User> mates = kind.mates().stream()
                .map(m -> ensureUser(m.key(), m.nickname(), m.githubUsername(), Role.USER))
                .toList();
        em.flush();

        // 같은 계정을 여러 사람이 동시에 눌러도 지우기·심기가 섞이지 않게 계정 행을 잠근다 (트랜잭션 끝까지)
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, account.getId());

        List<Long> ids = new ArrayList<>();
        ids.add(account.getId());
        mates.forEach(m -> ids.add(m.getId()));
        wipe(ids);

        Optional<Plan> max = planRepository.findByName("MAX");
        resetUser(account, kind.nickname(), kind.githubUsername(), kind.key(), kind.role(), max.map(Plan::getId).orElse(null));
        for (int i = 0; i < mates.size(); i++) {
            DemoKind.Mate m = kind.mates().get(i);
            resetUser(mates.get(i), m.nickname(), m.githubUsername(), m.key(), Role.USER, null);
        }

        seedAgreements(account);
        seedBilling(account, now, later);
        Map<Integer, SeededPr> prs = seedReposAndPrs(kind, account, mates, now, later);
        seedQuestions(prs, later);
        seedStandaloneReviews(account, now, later);
        seedLearning(account, now, later);
        seedSkills(account, now, later);
        seedPetAndNotifications(account, now, later);

        // 여기까지 저장한 것을 DB 에 내보내고 컨텍스트를 비운 뒤 날짜를 과거로 덮어쓴다
        em.flush();
        em.clear();
        for (Backdate b : later) {
            jdbc.update("UPDATE `" + b.table() + "` SET " + b.column() + " = ? WHERE id = ?", b.at(), b.id());
        }
        // PR 리뷰는 웹훅 경로라 언어가 비어 있다. 예시 저장소는 전부 자바라 약점이 "Java" 로 묶이게 채운다
        new NamedParameterJdbcTemplate(jdbc).update(
                "UPDATE reviews SET language = 'Java' WHERE target_type = 'PR' AND user_id IN (:ids)",
                new MapSqlParameterSource("ids", ids));

        seedWeeklyReports(account, today);
        return userRepository.findById(account.getId()).orElseThrow();
    }

    // ── 계정 ──

    private User ensureUser(String key, String nickname, String githubUsername, Role role) {
        return userRepository.findByGithubId(key).orElseGet(() -> {
            // 이메일·비밀번호 없음 → 이메일 로그인으로는 들어올 수 없다. 테스트 로그인 길로만 토큰이 나간다
            User user = User.builder().email(null).password(null).nickname(nickname).build();
            user.linkGithub(key, githubUsername, null); // 토큰은 없다. GitHub 을 부르는 길은 DemoGuardFilter 가 예시 응답으로 대신한다
            user.changeRole(role);
            return userRepository.save(user);
        });
    }

    // 사용자가 바꿔 놓은 프로필·2단계 인증·상태를 처음 값으로 되돌린다
    private void resetUser(User user, String nickname, String githubUsername, String key, Role role, Long planId) {
        user.updateProfile(nickname, Level.INTERMEDIATE, INTERESTS);
        user.completeOnboarding(Level.INTERMEDIATE, INTERESTS, true);
        user.linkGithub(key, githubUsername, null);
        user.disableTotp();
        user.resetLoginFailCount();
        user.changeStatus(UserStatus.ACTIVE);
        user.changeRole(role);
        user.updatePlanId(planId);
    }

    // ── 지우기 ──

    private void wipe(List<Long> ids) {
        NamedParameterJdbcTemplate n = new NamedParameterJdbcTemplate(jdbc);
        MapSqlParameterSource p = new MapSqlParameterSource("ids", ids);

        List<Long> repoIds = n.queryForList("SELECT id FROM repositories WHERE user_id IN (:ids)", p, Long.class);
        List<Long> prIds = repoIds.isEmpty() ? List.of()
                : n.queryForList("SELECT id FROM pull_requests WHERE repo_id IN (:r)",
                new MapSqlParameterSource("r", repoIds), Long.class);
        MapSqlParameterSource rp = new MapSqlParameterSource("ids", ids)
                .addValue("prs", prIds.isEmpty() ? List.of(-1L) : prIds)
                .addValue("repos", repoIds.isEmpty() ? List.of(-1L) : repoIds);

        List<Long> reviewIds = n.queryForList(
                "SELECT id FROM reviews WHERE user_id IN (:ids) OR pr_id IN (:prs)", rp, Long.class);
        if (!reviewIds.isEmpty()) {
            MapSqlParameterSource r = new MapSqlParameterSource("r", reviewIds);
            n.update("DELETE FROM review_questions WHERE review_id IN (:r)", r);
            n.update("DELETE FROM review_issues WHERE review_id IN (:r)", r);
            n.update("DELETE FROM reviews WHERE id IN (:r)", r);
        }
        n.update("DELETE FROM pull_requests WHERE id IN (:prs)", rp);
        n.update("DELETE FROM repo_invitations WHERE repo_id IN (:repos) OR invited_user_id IN (:ids) OR inviter_id IN (:ids)", rp);
        n.update("DELETE FROM repo_members WHERE repo_id IN (:repos) OR user_id IN (:ids)", rp);
        n.update("DELETE FROM repositories WHERE id IN (:repos)", rp);

        n.update("DELETE FROM quiz_submissions WHERE user_id IN (:ids)", p);
        n.update("DELETE FROM learning_card_quizzes WHERE card_id IN (SELECT id FROM learning_cards WHERE user_id IN (:ids))", p);
        n.update("DELETE FROM learning_cards WHERE user_id IN (:ids)", p);

        n.update("DELETE FROM ai_skill_favorites WHERE user_id IN (:ids)", p);
        n.update("DELETE FROM ai_skills WHERE user_id IN (:ids)", p); // AI 가 이 계정에 만들어 준 스킬만. 공용(user_id NULL)은 안 걸린다
        n.update("DELETE FROM ai_skill_recommendations WHERE user_id IN (:ids)", p);
        n.update("DELETE FROM weakness_stats WHERE user_id IN (:ids)", p);
        n.update("DELETE FROM user_streaks WHERE user_id IN (:ids)", p);
        n.update("DELETE FROM pet_states WHERE user_id IN (:ids)", p);
        n.update("DELETE FROM `weekly-reports` WHERE user_id IN (:ids)", p);
        n.update("DELETE FROM notifications WHERE user_id IN (:ids)", p);
        n.update("DELETE FROM credit_usage WHERE user_id IN (:ids)", p);
        n.update("DELETE FROM user_agreements WHERE user_id IN (:ids)", p);
        n.update("DELETE FROM inquiries WHERE user_id IN (:ids)", p);

        n.update("DELETE FROM subscription_history WHERE subscription_id IN (SELECT id FROM subscriptions WHERE user_id IN (:ids))", p);
        n.update("DELETE FROM subscriptions WHERE user_id IN (:ids)", p);
        n.update("DELETE FROM payment_methods WHERE user_id IN (:ids)", p);
    }

    // ── 약관 · 요금제 ──

    // 지금 약관 버전 그대로 동의한 상태 — 로그인하자마자 재동의 화면이 뜨지 않게
    private void seedAgreements(User account) {
        for (Term t : termRepository.findAllByOrderByIdAsc()) {
            userAgreementRepository.save(UserAgreement.of(account.getId(), t.getId(), t.getVersion()));
        }
    }

    // FREE → PRO → MAX 로 올려 온 구독 이력 + 오늘 크레딧 사용량.
    // 결제수단은 화면 표시용 가짜 값이다. 실제 결제 요청은 DemoGuardFilter 가 막는다
    private void seedBilling(User account, LocalDateTime now, List<Backdate> later) {
        Optional<Plan> free = planRepository.findByName("FREE");
        Optional<Plan> pro = planRepository.findByName("PRO");
        Optional<Plan> max = planRepository.findByName("MAX");

        CreditUsage usage = new CreditUsage();
        usage.start(account.getId(), now.toLocalDate(), max.map(Plan::getDailyCreditLimit).orElse(20));
        usage.consume(8);
        creditUsageRepository.save(usage);

        if (max.isEmpty()) return; // 요금제 표가 비어 있는 DB — 구독 없이 FREE 로 보인다

        PaymentMethod pm = paymentMethodRepository.save(new PaymentMethod(account.getId(),
                "demo-customer-" + account.getId(), "demo-billing-" + account.getId(), "4518-****-****-2030"));
        Subscription sub = new Subscription();
        sub.start(account.getId(), max.get().getId(), pm.getId());
        subscriptionRepository.save(sub);

        LocalDateTime started = now.minusDays(42);
        later.add(new Backdate("subscriptions", "started_at", sub.getId(), started));
        later.add(new Backdate("subscriptions", "expires_at", sub.getId(), started.plusMonths(2)));
        later.add(new Backdate("payment_methods", "created_at", pm.getId(), started.minusMinutes(3)));

        if (free.isPresent() && pro.isPresent()) {
            history(sub.getId(), free.get().getId(), pro.get().getId(), ChangeType.UPGRADE, pro.get().getPrice(), started, later);
            history(sub.getId(), pro.get().getId(), max.get().getId(), ChangeType.UPGRADE,
                    max.get().getPrice() - pro.get().getPrice(), now.minusDays(28), later);
        }
        history(sub.getId(), max.get().getId(), max.get().getId(), ChangeType.RENEWAL, max.get().getPrice(), started.plusMonths(1), later);
    }

    private void history(Long subId, Long from, Long to, ChangeType type, int amount, LocalDateTime at, List<Backdate> later) {
        SubscriptionHistory h = new SubscriptionHistory();
        h.record(subId, from, to, type, amount);
        subscriptionHistoryRepository.save(h);
        later.add(new Backdate("subscription_history", "changed_at", h.getId(), at));
    }

    // ── 저장소 · PR · 리뷰 ──

    private Map<Integer, SeededPr> seedReposAndPrs(DemoKind kind, User account, List<User> mates,
                                                   LocalDateTime now, List<Backdate> later) {
        long base = kind.githubRepoIdBase();
        GithubRepository shop = githubRepositoryRepository.save(GithubRepository.link(account.getId(),
                String.valueOf(base + 1), DemoSeedData.REPO_NAMES[0], false, REPO_OWNER + "/" + DemoSeedData.REPO_NAMES[0]));
        GithubRepository board = githubRepositoryRepository.save(GithubRepository.link(account.getId(),
                String.valueOf(base + 2), DemoSeedData.REPO_NAMES[1], true, REPO_OWNER + "/" + DemoSeedData.REPO_NAMES[1]));
        later.add(new Backdate("repositories", "linked_at", shop.getId(), now.minusDays(90)));
        later.add(new Backdate("repositories", "linked_at", board.getId(), now.minusDays(40)));

        List<RepoMember> members = new ArrayList<>();
        members.add(RepoMember.of(shop.getId(), account.getId(), RepoRole.OWNER));
        mates.forEach(m -> members.add(RepoMember.of(shop.getId(), m.getId(), RepoRole.MEMBER)));
        members.add(RepoMember.of(board.getId(), account.getId(), RepoRole.OWNER));
        members.add(RepoMember.of(board.getId(), mates.get(0).getId(), RepoRole.MEMBER));
        repoMemberRepository.saveAll(members);
        for (RepoMember m : members) {
            later.add(new Backdate("repo_members", "joined_at", m.getId(), now.minusDays(m.getRepoId().equals(shop.getId()) ? 89 : 39)));
        }

        Map<Integer, SeededPr> byNumber = new HashMap<>();
        for (DemoSeedData.Pr p : DemoSeedData.PRS) {
            GithubRepository repo = p.repo() == 0 ? shop : board;
            User author = p.author() == 0 ? account : mates.get(p.author() - 1);
            String login = p.author() == 0 ? kind.githubUsername() : kind.mates().get(p.author() - 1).githubUsername();

            PullRequest pr = PullRequest.open(repo.getId(), p.number(), p.title(), author.getId(), login);
            pr.selectModel(p.model());
            pr.markReviewed();
            pullRequestRepository.save(pr);

            LocalDateTime at = when(now, p.weeksAgo(), p.day(), 13 + p.number() % 6, (p.number() * 7) % 60);
            later.add(new Backdate("pull_requests", "created_at", pr.getId(), at.minusMinutes(40)));
            later.add(new Backdate("pull_requests", "updated_at", pr.getId(), at));
            Long reviewId = saveReview(Review.createFromPr(author.getId(), pr.getId(), p.model()), p.issues(), at, later);
            byNumber.put(p.number(), new SeededPr(pr, author.getId(), reviewId, at));
        }

        for (DemoSeedData.ReReview rr : DemoSeedData.RE_REVIEWS) {
            SeededPr first = byNumber.get(rr.prNumber());
            LocalDateTime at = first.reviewedAt().plusDays(rr.daysAfter());
            Long reviewId = saveReview(Review.createFromPr(first.authorId(), first.pr().getId(), rr.model()), rr.issues(), at, later);
            later.add(new Backdate("pull_requests", "updated_at", first.pr().getId(), at));
            byNumber.put(rr.prNumber(), new SeededPr(first.pr(), first.authorId(), reviewId, at));
        }
        return byNumber;
    }

    private Long saveReview(Review review, List<DemoSeedData.Issue> issues, LocalDateTime at, List<Backdate> later) {
        review.markCompleted();
        reviewRepository.save(review);
        later.add(new Backdate("reviews", "created_at", review.getId(), at));
        for (DemoSeedData.Issue s : issues) {
            ReviewIssue issue = ReviewIssue.of(review.getId(), s.category(), s.severity(), s.file(), s.line(), s.text());
            switch (s.status()) {
                case 'R' -> issue.finalizeAs(IssueStatus.RESOLVED);
                case 'I' -> issue.finalizeAs(IssueStatus.IGNORED);
                case 'P' -> issue.requestResolve(); // 팀원이 "고쳤어요" 를 눌러 팀장 승인을 기다리는 상태
                default -> { }
            }
            reviewIssueRepository.save(issue);
            later.add(new Backdate("review_issues", "created_at", issue.getId(), at));
            later.add(new Backdate("review_issues", "updated_at", issue.getId(), s.status() == 'O' ? at : at.plusHours(20)));
        }
        return review.getId();
    }

    private void seedQuestions(Map<Integer, SeededPr> prs, List<Backdate> later) {
        int k = 0;
        for (DemoSeedData.Question q : DemoSeedData.QUESTIONS) {
            SeededPr target = prs.get(q.prNumber());
            ReviewQuestion saved = reviewQuestionRepository.save(ReviewQuestion.of(target.latestReviewId(), q.question(), q.answer()));
            later.add(new Backdate("review_questions", "created_at", saved.getId(), target.reviewedAt().plusMinutes(25L * ++k)));
        }
    }

    // 스튜디오에서 붙여넣기·파일 올리기로 받은 리뷰 (리뷰 이력 화면, Python 약점)
    private void seedStandaloneReviews(User account, LocalDateTime now, List<Backdate> later) {
        Review paste = Review.createPaste(account.getId(), DemoSeedData.PASTE_CODE, "Java", "claude-haiku-4-5");
        saveReview(paste, DemoSeedData.PASTE_ISSUES, when(now, 3, 3, 21, 10), later);
        Review upload = Review.createUpload(account.getId(), DemoSeedData.UPLOAD_CODE, "Python",
                DemoSeedData.UPLOAD_NAME, "claude-haiku-4-5");
        saveReview(upload, DemoSeedData.UPLOAD_ISSUES, when(now, 1, 5, 22, 40), later);
    }

    // ── 학습 카드 · 퀴즈 · 연속 학습일 ──

    // 제출일: 8주 전부터 듬성듬성 20일 + 어제까지 12일 연속. 오늘은 비워 둬서 퀴즈를 풀면 연속일이 13으로 오르는 게 보인다
    private static final int[] OLD_DAYS = {56, 55, 52, 50, 48, 46, 44, 41, 39, 37, 35, 33, 31, 29, 27, 24, 22, 19, 17, 15};
    private static final int STREAK_DAYS = 12;

    private void seedLearning(User account, LocalDateTime now, List<Backdate> later) {
        List<DemoSeedData.Card> specs = DemoSeedData.CARDS;
        List<LocalDateTime> slots = new ArrayList<>();
        for (int d : OLD_DAYS) slots.add(now.toLocalDate().minusDays(d).atTime(19 + d % 3, (d * 11) % 60));
        for (int d = STREAK_DAYS; d >= 1; d--) slots.add(now.toLocalDate().minusDays(d).atTime(20 + d % 2, (d * 13) % 60));

        // 카드마다 몇 번 제출할지, 몇 번째 칸부터 시작할지 (먼저 만든 카드일수록 일찍 시작한다)
        int[] need = specs.stream().mapToInt(c -> c.correct() + c.wrong()).toArray();
        int[] start = {0, 3, 9, 16, 22, Integer.MAX_VALUE};
        int[] done = new int[specs.size()];
        List<int[]> plan = new ArrayList<>(); // {slotIndex, cardIndex, k}
        for (int s = 0; s < slots.size(); s++) {
            List<Integer> active = new ArrayList<>();
            for (int c = 0; c < specs.size(); c++) {
                if (start[c] <= s && done[c] < need[c]) active.add(c);
            }
            if (active.isEmpty()) {
                for (int c = 0; c < specs.size(); c++) {
                    if (done[c] < need[c]) { active.add(c); break; }
                }
            }
            if (active.isEmpty()) break;
            int c = active.get(s % active.size());
            plan.add(new int[]{s, c, done[c]++});
        }

        // 카드 생성일 = 첫 제출 전날 (제출이 없는 카드는 이틀 전)
        LocalDateTime[] createdAt = new LocalDateTime[specs.size()];
        for (int[] row : plan) {
            if (createdAt[row[1]] == null) createdAt[row[1]] = slots.get(row[0]).minusDays(1).withHour(18);
        }

        List<LearningCard> cards = new ArrayList<>();
        List<List<LearningCardQuiz>> quizzes = new ArrayList<>();
        for (int c = 0; c < specs.size(); c++) {
            DemoSeedData.Card spec = specs.get(c);
            LearningCard card = LearningCard.create(account.getId(), spec.category(), spec.level(), spec.language(), spec.model(),
                    spec.concept(), spec.why(), spec.bad(), spec.good(), spec.diff(),
                    spec.keyPoints(), spec.pitfalls(), spec.selfCheck());
            card.updateBookmark(spec.bookmarked());
            card.updateCompleted(spec.completed());
            learningCardRepository.save(card);
            if (createdAt[c] == null) createdAt[c] = now.minusDays(2);
            later.add(new Backdate("learning_cards", "created_at", card.getId(), createdAt[c]));
            cards.add(card);

            List<LearningCardQuiz> qs = new ArrayList<>();
            for (int q = 0; q < spec.quizzes().size(); q++) {
                DemoSeedData.Quiz s = spec.quizzes().get(q);
                LearningCardQuiz quiz = learningCardQuizRepository.save(LearningCardQuiz.create(
                        card.getId(), s.type(), spec.model(), s.question(), s.options(), s.answer(), s.explain()));
                later.add(new Backdate("learning_card_quizzes", "created_at", quiz.getId(), createdAt[c].plusMinutes(10L + q)));
                qs.add(quiz);
            }
            quizzes.add(qs);
        }

        for (int[] row : plan) {
            int c = row[1];
            int k = row[2];
            DemoSeedData.Card spec = specs.get(c);
            boolean correct = !wrongPositions(spec).contains(k);
            int solvable = spec.quizzes().size() - spec.unsolved();
            int qi = k % solvable;
            DemoSeedData.Quiz qs = spec.quizzes().get(qi);
            LearningCard card = cards.get(c);
            if (correct) card.recordCorrect();
            QuizSubmission sub = quizSubmissionRepository.save(QuizSubmission.create(quizzes.get(c).get(qi).getId(),
                    account.getId(), correct ? qs.answer() : qs.wrongAnswer(), correct, card.getGrade()));
            later.add(new Backdate("quiz_submissions", "submitted_at", sub.getId(), slots.get(row[0])));
        }

        // 학습 계획: 하나는 사흘 전에 달력에 등록, 하나는 만들기만 하고 미등록
        cards.get(DemoSeedData.PLAN_CARD).updateStudyPlan(DemoSeedData.PLAN_STEPS.strip());
        cards.get(DemoSeedData.PLAN_CARD).registerStudyPlan(now.toLocalDate().minusDays(DemoSeedData.PLAN_STARTED_DAYS_AGO));
        cards.get(DemoSeedData.PLAN_DRAFT_CARD).updateStudyPlan(DemoSeedData.PLAN_DRAFT_STEPS.strip());

        UserStreak streak = UserStreak.create(account.getId());
        for (int d = STREAK_DAYS; d >= 1; d--) streak.recordSubmission(now.toLocalDate().minusDays(d));
        userStreakRepository.save(streak);
    }

    // 카드마다 오답이 몇 번째 제출인지 — 앞쪽 제출에 섞어 둔다
    private static List<Integer> wrongPositions(DemoSeedData.Card spec) {
        int total = spec.correct() + spec.wrong();
        return java.util.stream.Stream.of(1, 3, 0, 2, 4, 5)
                .filter(p -> p < total)
                .limit(spec.wrong())
                .toList();
    }

    // ── 스킬 추천 · 코기 · 알림 ──

    private void seedSkills(User account, LocalDateTime now, List<Backdate> later) {
        AiSkillRecommendation byWeakness = aiSkillRecommendationRepository.save(AiSkillRecommendation.of(account.getId(),
                DemoSeedData.SKILL_BY_WEAKNESS_INPUT, DemoSeedData.SKILL_BY_WEAKNESS_RESULT.strip(), "BY_WEAKNESS"));
        later.add(new Backdate("ai_skill_recommendations", "created_at", byWeakness.getId(), now.minusDays(2).withHour(21)));
        AiSkillRecommendation free = aiSkillRecommendationRepository.save(AiSkillRecommendation.of(account.getId(),
                DemoSeedData.SKILL_FREE_INPUT, DemoSeedData.SKILL_FREE_RESULT.strip(), "FREE_TEXT"));
        later.add(new Backdate("ai_skill_recommendations", "created_at", free.getId(), now.minusDays(5).withHour(22)));

        // 공용 스킬 두 개에 별을 달아 둔다 (공용 스킬이 없는 DB 면 건너뜀)
        List<Long> shared = jdbc.queryForList("SELECT id FROM ai_skills WHERE user_id IS NULL ORDER BY id LIMIT 2", Long.class);
        shared.forEach(id -> aiSkillFavoriteRepository.save(AiSkillFavorite.of(account.getId(), id)));
    }

    private void seedPetAndNotifications(User account, LocalDateTime now, List<Backdate> later) {
        PetState pet = PetState.create(account.getId());
        pet.update(240, 72, 88, 64, 420, now.toLocalDate().minusDays(1)); // 오늘 출석 코인은 아직 안 받은 상태
        petStateRepository.save(pet);

        for (String[] n : DemoSeedData.NOTIFICATIONS) {
            Notification saved = notificationRepository.save(Notification.of(account.getId(), n[0], n[1], n[2], n[3]));
            later.add(new Backdate("notifications", "created_at", saved.getId(), now.minusHours(Long.parseLong(n[4]))));
        }
    }

    // ── 주간 리포트 8주치 ──
    // 심은 이슈에서 그대로 수치를 뽑는다 — 월요일 배치(WeeklyReportSender)와 같은 계산, 같은 문장
    private void seedWeeklyReports(User account, LocalDate today) {
        LocalDate thisMonday = today.with(DayOfWeek.MONDAY);
        List<Object[]> stamps = new ArrayList<>();
        for (int w = 8; w >= 1; w--) {
            LocalDate start = thisMonday.minusWeeks(w);
            LocalDateTime from = start.atStartOfDay();
            LocalDateTime to = start.plusWeeks(1).atStartOfDay();

            Object[] cur = reviewIssueRepository.weeklySummary(account.getId(), from, to).get(0);
            int issues = ((Number) cur[0]).intValue();
            if (issues == 0) continue;
            int resolved = cur[1] == null ? 0 : ((Number) cur[1]).intValue();
            int prev = ((Number) reviewIssueRepository.weeklySummary(account.getId(), from.minusWeeks(1), from).get(0)[0]).intValue();
            List<Object[]> cats = reviewIssueRepository.categoryBreakdown(account.getId(), from, to);
            String top = cats.isEmpty() ? null : String.valueOf(cats.get(0)[0]);
            int rate = (int) Math.round(resolved * 100.0 / issues);
            String trend = prev == 0 ? "" : issues < prev ? " 전주보다 줄었어요!" : issues > prev ? " 전주보다 늘었어요." : " 전주와 비슷해요.";
            String summary = "지난주 발생 " + issues + "건 중 " + resolved + "건 해결(해결률 " + rate + "%)." + trend;

            WeeklyReport report = weeklyReportRepository.save(WeeklyReport.of(
                    account.getId(), start, start.plusDays(6), issues, resolved, prev, top, summary));
            stamps.add(new Object[]{report.getId(), start.plusWeeks(1)});
        }
        em.flush();
        for (Object[] s : stamps) {
            jdbc.update("UPDATE `weekly-reports` SET created_at = ? WHERE id = ?", s[1], s[0]);
        }
    }

    // 몇 주 전 몇 요일 몇 시. 이번 주(weeksAgo=0)는 지금보다 조금 앞 시각 — 이번 주 월요일 밖으로 나가지 않게
    private static LocalDateTime when(LocalDateTime now, int weeksAgo, int day, int hour, int minute) {
        LocalDate monday = now.toLocalDate().with(DayOfWeek.MONDAY);
        if (weeksAgo == 0) {
            LocalDateTime t = now.minusMinutes(50L + minute % 30);
            LocalDateTime floor = monday.atTime(0, 5);
            return t.isBefore(floor) ? floor : t;
        }
        return monday.minusWeeks(weeksAgo).plusDays(day - 1L).atTime(hour, minute);
    }
}
