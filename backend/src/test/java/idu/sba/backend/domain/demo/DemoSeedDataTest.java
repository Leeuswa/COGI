package idu.sba.backend.domain.demo;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 예시 데이터가 화면을 채울 만큼 넉넉한지, 같은 문장을 되풀이하지 않는지
class DemoSeedDataTest {

    private static List<DemoSeedData.Issue> allIssues() {
        List<DemoSeedData.Issue> all = new ArrayList<>();
        DemoSeedData.PRS.forEach(p -> all.addAll(p.issues()));
        DemoSeedData.RE_REVIEWS.forEach(r -> all.addAll(r.issues()));
        all.addAll(DemoSeedData.PASTE_ISSUES);
        all.addAll(DemoSeedData.UPLOAD_ISSUES);
        return all;
    }

    @Test
    void 지적_문장은_한_번씩만_쓴다() {
        List<DemoSeedData.Issue> all = allIssues();
        Set<String> texts = new HashSet<>();
        all.forEach(i -> texts.add(i.text()));
        assertThat(texts).hasSize(all.size());
        assertThat(all.size()).isGreaterThanOrEqualTo(50);
    }

    @Test
    void 지적_종류는_다섯_가지_모두_있고_각각_세_번_넘게_나온다() {
        for (var c : idu.sba.backend.domain.review.entity.IssueCategory.values()) {
            long mine = DemoSeedData.PRS.stream().filter(p -> p.author() == 0)
                    .flatMap(p -> p.issues().stream()).filter(i -> i.category() == c && i.status() != 'I').count();
            assertThat(mine).as(c.name()).isGreaterThanOrEqualTo(3);
        }
    }

    @Test
    void 여덟_주_전부터_이번_주까지_빠진_주가_없다() {
        Set<Integer> weeks = new HashSet<>();
        DemoSeedData.PRS.stream().filter(p -> p.author() == 0).forEach(p -> weeks.add(p.weeksAgo()));
        assertThat(weeks).containsExactlyInAnyOrder(0, 1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void 성장_추이_최근_12주의_앞쪽도_팀_PR_이_있다() {
        Set<Integer> weeks = new HashSet<>();
        DemoSeedData.PRS.forEach(p -> weeks.add(p.weeksAgo()));
        for (int w = 0; w < 12; w++) assertThat(weeks).as(w + "주 전").contains(w);
        // 9주 전보다 오래된 PR 은 팀원 것만 — 체험 계정의 약점 · 주간 리포트 수는 그대로
        assertThat(DemoSeedData.PRS.stream().filter(p -> p.weeksAgo() > 8)).allMatch(p -> p.author() != 0);
        // 첫 주(11주 전) 이슈가 이번 주보다 많아야 「좋은 신호예요」 가 보인다
        int first = DemoSeedData.PRS.stream().filter(p -> p.weeksAgo() == 11).mapToInt(p -> p.issues().size()).sum();
        int last = DemoSeedData.PRS.stream().filter(p -> p.weeksAgo() == 0).mapToInt(p -> p.issues().size()).sum();
        assertThat(first).isGreaterThan(last);
    }

    @Test
    void 퀴즈는_푼_것과_안_푼_것이_섞여_있고_등급이_골고루다() {
        int submissions = 0;
        int unsolved = 0;
        for (DemoSeedData.Card c : DemoSeedData.CARDS) {
            submissions += c.correct() + c.wrong();
            unsolved += c.unsolved();
            if (c.correct() + c.wrong() > 0) assertThat(c.quizzes().size()).isGreaterThan(c.unsolved()); // 제출할 문제가 남아 있어야 한다
        }
        assertThat(submissions).isEqualTo(32); // DemoSeeder 의 제출 날짜 칸 수(20 + 12)와 같아야 한다
        assertThat(unsolved).isGreaterThan(0);
        assertThat(DemoSeedData.CARDS).extracting(DemoSeedData.Card::correct).contains(8, 7, 4, 3, 1, 0);
    }

    @Test
    void 학습계획과_스킬추천은_화면이_읽는_JSON_이다() {
        JsonMapper m = JsonMapper.builder().build();
        JsonNode plan = m.readTree(DemoSeedData.PLAN_STEPS);
        assertThat(plan.isArray()).isTrue();
        assertThat(plan.size()).isEqualTo(8);
        assertThat(plan.get(0).has("dayOffset")).isTrue();
        assertThat(m.readTree(DemoSeedData.PLAN_DRAFT_STEPS).size()).isEqualTo(3);
        assertThat(m.readTree(DemoSeedData.SKILL_BY_WEAKNESS_RESULT).get("items").size()).isEqualTo(3);
        assertThat(m.readTree(DemoSeedData.SKILL_FREE_RESULT).get("items").size()).isEqualTo(2);
    }

    @Test
    void 테스트_계정_표시는_실제_GitHub_값과_겹치지_않는다() {
        for (DemoKind k : DemoKind.values()) {
            assertThat(k.key()).startsWith(DemoAccountRegistry.KEY_PREFIX); // 실제 GitHub id 는 숫자뿐
            assertThat(k.githubUsername()).contains("."); // 화면에는 연동 계정으로 보이되 실제 GitHub 아이디와 겹치지 않게
            k.mates().forEach(mate -> {
                assertThat(mate.key()).startsWith(DemoAccountRegistry.KEY_PREFIX);
                assertThat(mate.githubUsername()).contains("."); // GitHub 아이디에는 점이 못 들어간다
            });
            assertThat(DemoKind.fromPath(k.path())).isEqualTo(k);
        }
        assertThat(DemoKind.fromPath("root")).isNull();
    }

    @Test
    void 관리자_화면_개인정보_가리기() {
        assertThat(DemoAdminMaskingAdvice.maskEmail("someone@example.com")).isEqualTo("s***@***.com");
        assertThat(DemoAdminMaskingAdvice.maskEmail("broken")).isEqualTo("***");
        assertThat(DemoAdminMaskingAdvice.maskName("코기유저")).isEqualTo("코**");
    }
}
