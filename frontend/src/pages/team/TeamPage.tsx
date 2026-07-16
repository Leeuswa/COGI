/*
 * 팀 관리 (API-034 링크 초대 확장)
 * 한 라우트(/app/team)에서 역할별로 다른 화면:
 *   - 팀장(OWNER): 초대 링크 복사/공유 → 링크 타고 온 합류 신청 수락/거절 → 멤버 내보내기
 *                  + 팀 리뷰 현황 바로가기 — "팀장이 관리할 일"은 전부 여기로
 *   - 팀원(MEMBER): 팀 정보·멤버 확인, 팀 나가기 — "팀원이 할 일"만 담백하게
 * 목 전용: user 계정으로 로그인하면 팀장 화면, adim 계정이면 팀원 화면이 보인다 (README 참고)
 */
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { useGame } from '../../context/GameContext';
import { PageHead } from '../../components/ui';

export default function TeamPage() {
  const { user } = useAuth();
  const { notify } = useGame();
  const [team, setTeam] = useState(null);
  const [busy, setBusy] = useState(false);
  const [ghId, setGhId] = useState('');   // 가입자 초대 (GitHub 아이디)
  const [newTeam, setNewTeam] = useState(''); // 팀장으로 시작 — 새 팀 이름
  const [invEmail, setInvEmail] = useState(''); // 비회원 초대 (이메일)

  useEffect(() => { api.getMyTeam(user.loginId).then(setTeam); }, [user.loginId]);

  if (!team) return <main className="app-main"><div className="panel"><p className="note">불러오는 중…</p></div></main>;

  // 아직 팀이 없는 계정 — ① 초대가 와 있으면 수락 동선 ② 없으면 팀장으로 시작 (요구 #10~12)
  if (team.none) {
    const inv = team.invite;
    const needsGithub = inv?.via === 'EMAIL' && !user.githubUsername; // 이메일 초대는 연동이 먼저
    return (
      <main className="app-main">
        <PageHead badge="TEAM" title="아직 팀이 없어요"
          lead={"초대를 수락해 합류하거나,\n직접 팀을 만들어 팀장이 될 수 있어요."} />

        {/* 도착해 있는 초대 */}
        {inv && (
          <div className="panel" style={{ borderColor: 'var(--coral)' }}>
            <h3>{inv.via === 'GITHUB' ? '👥' : '📧'} {inv.teamName}에서 초대가 왔어요</h3>
            {needsGithub ? (
              <>
                <p className="note sm" style={{ marginBottom: 12 }}>
                  이메일 초대는 GitHub 연동을 마쳐야 수락할 수 있어요.{'\n'}연동이 끝나면 이 화면으로 다시 안내해드립니다.
                </p>
                <Link className="btn co sm" to="/app/repos" state={{ fromInvite: true }}>🐙 GitHub 연동하러 가기</Link>
              </>
            ) : (
              <>
                <p className="note sm" style={{ marginBottom: 12 }}>
                  {inv.via === 'GITHUB'
                    ? '팀장이 GitHub 아이디로 직접 초대했어요.\n수락하면 별도 승인 없이 바로 합류됩니다.'
                    : '이메일 초대예요. 수락하면 바로 합류됩니다.'}
                </p>
                <button className="btn co sm" disabled={busy}
                  onClick={async () => {
                    setBusy(true);
                    try {
                      const res = await api.acceptTeamInvite();
                      setTeam(res.team); // 수락 즉시 팀원 화면으로 전환
                      notify(`${inv.teamName}에 합류했어요 🎉`);
                    } finally { setBusy(false); }
                  }}>
                  {busy ? '합류 중…' : '초대 수락하고 바로 합류'}
                </button>
              </>
            )}
          </div>
        )}

        {/* 직접 팀장 되기 — 레포만 연동해둔 사람의 시작점 */}
        <div className="panel">
          <h3>👑 팀장으로 시작하기</h3>
          <p className="note sm" style={{ marginBottom: 12 }}>
            팀을 만들면 초대 링크가 발급되고, 팀원들의 PR 리뷰를 결재할 수 있어요.
          </p>
          <div className="row">
            <input type="text" value={newTeam} placeholder="팀 이름 (예: Team Rocket)"
              style={{ padding: '11px 13px', border: '3px solid var(--navy)', fontSize: 13.5, flex: 1, maxWidth: 300 }}
              onChange={(e) => setNewTeam(e.target.value)} />
            <button className="btn co sm" disabled={!newTeam.trim() || busy}
              onClick={async () => {
                setBusy(true);
                try {
                  const res = await api.createTeam(newTeam.trim());
                  setTeam(res.team); // 바로 팀장 화면
                  notify(`${newTeam.trim()} 팀이 만들어졌어요. 이제 팀장입니다 👑`);
                } finally { setBusy(false); }
              }}>팀 만들기</button>
          </div>
          {!user.githubUsername && (
            <p className="note sm" style={{ marginTop: 10 }}>
              팀 리뷰를 돌리려면 <Link className="link-line" to="/app/repos">GitHub 연동</Link>도 잊지 마세요.
            </p>
          )}
        </div>
      </main>
    );
  }

  const isOwner = team.myRole === 'OWNER';
  const inviteUrl = `${window.location.origin}/invite/${team.inviteCode}`;

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(inviteUrl);
      notify('초대 링크를 복사했어요. 팀원에게 공유하세요');
    } catch {
      notify('복사가 막혔어요. 링크를 직접 드래그해서 복사해주세요');
    }
  };

  const decide = async (m, ok) => {
    setBusy(true);
    try {
      await api.decideJoin(m.id, ok);
      setTeam((t) => ({
        ...t,
        pending: t.pending.filter((x) => x.id !== m.id),
        members: ok ? [...t.members, { ...m, role: 'MEMBER', joinedAt: '오늘' }] : t.members,
      }));
      notify(ok ? `${m.name}님이 팀에 합류했어요 🎉` : `${m.name}님의 신청을 거절했어요`);
    } finally { setBusy(false); }
  };

  const kick = async (m) => {
    if (!window.confirm(`${m.name}님을 팀에서 내보낼까요?`)) return;
    await api.removeMember(m.id);
    setTeam((t) => ({ ...t, members: t.members.filter((x) => x.id !== m.id) }));
    notify(`${m.name}님을 내보냈어요`);
  };

  const leave = async () => {
    if (!window.confirm('정말 팀을 나갈까요?\n다시 들어오려면 초대 링크가 필요해요.')) return;
    await api.leaveTeam();
    notify('팀에서 나왔어요');
  };

  return (
    <main className="app-main">
      <PageHead badge={isOwner ? 'TEAM · OWNER' : 'TEAM'} badgeCls={isOwner ? 'co' : ''}
        title={team.name}
        lead={isOwner
          ? '팀장 페이지예요.\n초대 링크를 공유하고, 합류 신청을 승인하고, 멤버를 관리하세요.'
          : '팀원 페이지예요.\n팀 정보와 멤버를 확인할 수 있어요.'} />

      {/* ── 팀장 전용: 초대 링크 ── */}
      {isOwner && (
        <div className="panel">
          <h3>🔗 초대 링크</h3>
          <p className="note sm" style={{ marginBottom: 12 }}>
            이 링크로 들어온 사람이 로그인(비회원은 회원가입)하면 아래 "합류 대기"에 올라와요.
            {'\n'}수락해야 정식 팀원이 됩니다.
          </p>
          <div className="row">
            <code className="invite-url">{inviteUrl}</code>
            <button className="btn co sm" onClick={copyLink}>복사</button>
          </div>

          {/* 직접 초대 — 이미 가입한 사람은 GitHub 아이디로, 아직 회원이 아니면 이메일로 */}
          <div className="invite-direct">
            <div>
              <label className="note sm">이미 가입한 팀원 — GitHub 아이디로 초대</label>
              <div className="row">
                <input type="text" value={ghId} placeholder="예: su-dev"
                  onChange={(e) => setGhId(e.target.value)} />
                <button className="btn wh sm" disabled={!ghId.trim() || busy}
                  onClick={async () => {
                    setBusy(true);
                    try { await api.inviteToTeam({ githubUsername: ghId.trim() }); notify(`@${ghId.trim()} 님에게 초대를 보냈어요`); setGhId(''); }
                    finally { setBusy(false); }
                  }}>초대</button>
              </div>
            </div>
            <div>
              <label className="note sm">아직 회원이 아니면 — 이메일로 초대장 발송</label>
              <div className="row">
                <input type="email" value={invEmail} placeholder="예: friend@mail.com"
                  onChange={(e) => setInvEmail(e.target.value)} />
                <button className="btn wh sm" disabled={!invEmail.includes('@') || busy}
                  onClick={async () => {
                    setBusy(true);
                    try { await api.inviteToTeam({ email: invEmail.trim() }); notify(`${invEmail.trim()} 로 초대 메일을 보냈어요`); setInvEmail(''); }
                    finally { setBusy(false); }
                  }}>메일 발송</button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── 팀장 전용: 합류 대기 ── */}
      {isOwner && (
        <div className="panel">
          <h3>⏳ 합류 대기 {team.pending.length > 0 && <span className="chip hi">{team.pending.length}</span>}</h3>
          {team.pending.length === 0 ? (
            <p className="note sm">대기 중인 신청이 없어요. 링크를 공유해보세요.</p>
          ) : (
            team.pending.map((m) => (
              <div key={m.id} className="row" style={{ padding: '10px 0', borderBottom: '2px dashed #d9e4f5' }}>
                <div>
                  <b style={{ fontSize: 14 }}>{m.name}</b>
                  <p className="note xs">{m.email} · {m.requestedAt} 신청</p>
                </div>
                <span className="ml-auto" />
                <button className="btn co sm" onClick={() => decide(m, true)} disabled={busy}>수락</button>
                <button className="btn wh sm" onClick={() => decide(m, false)} disabled={busy}>거절</button>
              </div>
            ))
          )}
        </div>
      )}

      {/* ── 공통: 멤버 목록 (팀장에겐 내보내기 버튼) ── */}
      <div className="panel flush">
        <table className="tbl">
          <thead><tr><th>이름</th><th>이메일</th><th>역할</th><th>합류일</th>{isOwner && <th></th>}</tr></thead>
          <tbody>
            {team.members.map((m) => (
              <tr key={m.id}>
                <td><b>{m.name}</b></td>
                <td style={{ fontSize: 12.5 }}>{m.email}</td>
                <td><span className={`chip ${m.role === 'OWNER' ? 'co' : 'navy'}`}>{m.role === 'OWNER' ? '팀장' : '팀원'}</span></td>
                <td className="mono xs">{m.joinedAt}</td>
                {isOwner && (
                  <td>
                    {m.role !== 'OWNER' && (
                      <button className="btn wh sm" onClick={() => kick(m)}>내보내기</button>
                    )}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* ── 역할별 하단 액션 ── */}
      {isOwner ? (
        <div className="panel">
          <h3>팀 리뷰 현황</h3>
          <p className="note sm" style={{ marginBottom: 12 }}>
            팀원들의 PR 이슈와 성장 추이는 아래에서 이어서 관리하세요.
          </p>
          <div className="row">
            <Link className="btn wh sm" to="/app/prs">PR 이슈 결재 →</Link>
            <Link className="btn wh sm" to="/app/growth">팀 성장 추이 →</Link>
            <Link className="btn wh sm" to="/app/repos">레포 연동 관리 →</Link>
          </div>
        </div>
      ) : (
        <div className="panel">
          <h3>팀 나가기</h3>
          <p className="note sm" style={{ marginBottom: 12 }}>
            나가면 팀 리뷰와 통계에서 제외돼요.{'\n'}다시 들어오려면 팀장에게 초대 링크를 받아야 합니다.
          </p>
          <button className="btn wh sm" onClick={leave}>팀 나가기</button>
        </div>
      )}
    </main>
  );
}
