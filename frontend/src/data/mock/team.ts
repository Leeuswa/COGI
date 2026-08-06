// ── 팀 (초대 링크 기반 합류 + 팀장/팀원 분리 관리) ──
// 흐름: 팀장이 링크 공유 → 팀원이 /invite/{code} 접속 → 로그인(비회원은 가입) →
//       합류 신청(PENDING) → 팀장이 수락해야 정식 멤버.
// 목 전용 트릭: myRole 은 로그인 계정으로 가른다 — user 계정=OWNER(팀장 화면),
//              adim 계정=MEMBER(팀원 화면). 실서버는 team_members.role 로 내려준다.
// ── 계정별 알림 (종 아이콘) — 초대가 오면 여기로 뜬다 ──
// member: GitHub 아이디 초대 (수락 즉시 합류 — 팀장 재수락 불필요, 요구 #10)
// user  : 이메일 초대 (GitHub 연동을 마쳐야 합류 가능, 요구 #11)
export const mockNotifications = {
  team: [],
  member: [
    {
      id: 1,
      icon: "👥",
      link: "/app/team",
      title: "Team Fable 초대",
      text: "팀장이 GitHub 아이디(@su-dev)로 초대했어요.\n수락하면 바로 팀에 합류됩니다.",
    },
  ],
  user: [
    {
      id: 2,
      icon: "📧",
      link: "/app/team",
      title: "Team Fable 초대",
      text: "이메일로 초대가 도착했어요.\nGitHub 연동을 마치면 합류할 수 있어요.",
    },
    {
      id: 3,
      icon: "🎉",
      link: "/app/cards",
      title: "첫 학습 카드 도착",
      text: '"null 안전성" 카드가 준비됐어요.\n지금 풀면 연속 학습이 시작됩니다.',
    },
  ],
  admin: [],
};

export const mockTeam = {
  id: 1,
  name: "Team Fable",
  inviteCode: "FABLE-2026", // 초대 링크: {origin}/invite/FABLE-2026
  createdAt: "2026-06-20",
  members: [
    {
      id: 1,
      name: "정상연",
      email: "dev@fable.dev",
      role: "OWNER",
      joinedAt: "2026-06-20",
    },
    {
      id: 2,
      name: "이수환",
      email: "su@fable.dev",
      role: "MEMBER",
      joinedAt: "2026-06-22",
    },
    {
      id: 3,
      name: "홍성찬",
      email: "hong@fable.dev",
      role: "MEMBER",
      joinedAt: "2026-06-23",
    },
  ],
  // 링크 타고 들어와 로그인까지 마친 합류 대기자 — 팀장 수락/거절 대상
  pending: [
    { id: 9, name: "유지환", email: "yu@fable.dev", requestedAt: "2026-07-09" },
  ],
};
