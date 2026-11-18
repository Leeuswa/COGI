package idu.sba.backend.domain.demo;

import idu.sba.backend.domain.user.entity.Role;

import java.util.List;

/*
 * 테스트 로그인 계정 두 종류.
 * 계정은 githubId 칸에 "cogi-demo:" 로 시작하는 값을 넣어 찾는다. 실제 GitHub id 는 숫자뿐이라 겹칠 일이 없고,
 * githubUsername·토큰은 비워 두므로 화면에서는 GitHub 미연동 계정으로 보인다.
 * 이메일도 비워 둔다 — 주간 리포트 메일 같은 자동 발송이 가짜 주소로 나가지 않게.
 *
 * 팀원(mate)은 팀 화면·성장 추이를 채우려고 만든 가상의 팀원이다. 로그인할 수 없다.
 * 팀원 githubUsername 에는 점(.)을 넣는다. GitHub 아이디에는 점이 들어갈 수 없어 실제 계정과 겹치지 않는다.
 */
public enum DemoKind {

    USER("user", "cogi-demo:user", "체험 사용자", Role.USER, 9_100_000_100L,
            List.of(new Mate("cogi-demo:user-mate1", "도토리", "demo.dotori"),
                    new Mate("cogi-demo:user-mate2", "보리", "demo.bori"))),

    ADMIN("admin", "cogi-demo:admin", "체험 관리자", Role.ADMIN, 9_100_000_200L,
            List.of(new Mate("cogi-demo:admin-mate1", "호두", "demo.hodu"),
                    new Mate("cogi-demo:admin-mate2", "콩이", "demo.kongi")));

    public record Mate(String key, String nickname, String githubUsername) {}

    private final String path;
    private final String key;
    private final String nickname;
    private final Role role;
    private final long githubRepoIdBase; // 예시 저장소 github_repo_id 시작값 (실제 GitHub 저장소 번호보다 훨씬 큼)
    private final List<Mate> mates;

    DemoKind(String path, String key, String nickname, Role role, long githubRepoIdBase, List<Mate> mates) {
        this.path = path;
        this.key = key;
        this.nickname = nickname;
        this.role = role;
        this.githubRepoIdBase = githubRepoIdBase;
        this.mates = mates;
    }

    public String path() { return path; }
    public String key() { return key; }
    public String nickname() { return nickname; }
    public Role role() { return role; }
    public long githubRepoIdBase() { return githubRepoIdBase; }
    public List<Mate> mates() { return mates; }

    public static DemoKind fromPath(String path) {
        for (DemoKind k : values()) {
            if (k.path.equals(path)) return k;
        }
        return null;
    }
}
