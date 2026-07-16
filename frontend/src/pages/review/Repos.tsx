/*
 * 레포 연동 (REV-001, API-022/023 + 팀원 초대 API-029)
 * - GitHub 계정 레포 목록(private 포함)을 불러와서 골라 연동.
 * - 연동 = DB 저장 + Webhook 자동 등록 (FR-27). webhookId 가 붙으면 연동 완료 상태.
 * - GitHub 미연동 계정이면 목록 대신 연동 유도. (이메일 가입자는 마이페이지에서 연동)
 */
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { useGame } from '../../context/GameContext';
import { PageHead } from '../../components/ui';
import { SPR } from '../../assets/sprites';

export default function Repos() {
  const { user } = useAuth();
  const { notify } = useGame();
  const [repos, setRepos] = useState(null);
  const [invite, setInvite] = useState({}); // repoId → 입력 중인 GitHub 아이디
  const [inbox, setInbox] = useState([]);   // 내가 받은 대기 중 초대 (FR-37)

  useEffect(() => {
    if (user.githubUsername) api.getGithubRepos().then(setRepos);
    api.getMyInvitations().then(setInbox);
  }, [user.githubUsername]);

  // 초대 수락/거절 (API-030/030-1). 수락하면 그 레포 PR 목록이 열린다
  const respond = async (inv, accept) => {
    await api.respondInvite(inv.repoId, inv.inviteId, accept);
    setInbox((prev) => prev.filter((i) => i.inviteId !== inv.inviteId));
    notify(accept ? `${inv.repoName} 팀에 합류!` : '초대를 거절했어요');
  };

  const link = async (repo) => {
    const res = await api.linkRepo(repo.id);
    setRepos((prev) => prev.map((r) => (r.id === repo.id ? { ...r, webhookId: res.webhookId } : r)));
    notify(`${repo.repoName} 연동 완료! Webhook 등록됨`);
  };

  const sendInvite = async (repoId) => {
    const name = (invite[repoId] || '').trim();
    if (!name) return;
    await api.inviteMember(repoId, name);
    setInvite((p) => ({ ...p, [repoId]: '' }));
    notify(`@${name} 초대장 발송 (수락 대기)`);
  };

  // GitHub 미연동 상태 — 여기서 막고 마이페이지로 보낸다 (FR-13)
  if (!user.githubUsername) {
    return (
      <main className="app-main">
        <PageHead badge="REPOS" title="레포지토리 연동" lead={"레포를 연동하면 PR이 올라올 때마다 자동으로 리뷰가 돌아갑니다."} />
        <div className="empty">
          <img src={SPR.sad} alt="" />
          <p>GitHub 계정이 아직 연동되지 않았어요.<br />연동하면 레포 목록을 불러올 수 있습니다.</p>
          <Link className="btn co" style={{ marginTop: 16, display: 'inline-block' }} to="/app/my">
            마이페이지에서 GitHub 연동하기
          </Link>
        </div>
      </main>
    );
  }

  return (
    <main className="app-main">
      <PageHead
        badge="REPOS"
        title="레포지토리 연동"
        lead={`@${user.githubUsername} 계정의 레포 목록이에요.\n연동하면 PR이 올라올 때마다 자동으로 리뷰가 시작됩니다.`}
      />

      {/* 받은 초대함 — 대기 건이 있을 때만 (FR-37, API-030/030-1) */}
      {inbox.length > 0 && (
        <div className="panel" style={{ marginBottom: 22, background: '#fff6d6' }}>
          <h3>받은 초대 {inbox.length}건</h3>
          {inbox.map((inv) => (
            <div key={inv.inviteId} className="row">
              <span className="mono" style={{ fontSize: 13 }}>{inv.repoName}</span>
              <span className="note sm">@{inv.inviterGithub} 팀장이 초대했어요</span>
              <span className="ml-auto" style={{ display: 'flex', gap: 8 }}>
                <button className="btn sm" onClick={() => respond(inv, true)}>수락</button>
                <button className="btn wh sm" onClick={() => respond(inv, false)}>거절</button>
              </span>
            </div>
          ))}
        </div>
      )}

      {!repos ? (
        <div className="panel"><p className="note">GitHub에서 목록을 불러오는 중…</p></div>
      ) : (
        repos.map((r) => (
          <div key={r.id} className="panel">
            <div className="row">
              <b style={{ fontSize: 15 }}>{r.githubRepoId}</b>
              <span className={`chip ${r.isPrivate ? 'navy' : 'gray'}`}>{r.isPrivate ? 'PRIVATE' : 'PUBLIC'}</span>
              {r.webhookId
                ? <span className="chip low">LINKED</span>
                : <span className="chip gray">NOT LINKED</span>}
              <div className="ml-auto">
                {r.webhookId
                  ? <Link className="btn wh sm" to="/app/prs">PR 목록 보기 →</Link>
                  : <button className="btn co sm" onClick={() => link(r)}>연동하기</button>}
              </div>
            </div>

            {r.webhookId && (
              <>
                <p className="mono" style={{ fontSize: 12.5, color: 'var(--sub)', marginTop: 10 }}>
                  webhook: {r.webhookId} — PR opened/synchronize 이벤트 수신 중
                </p>
                {/* 팀원 초대 (FR-36): GitHub 아이디로 초대 → 수락하면 팀 대시보드 공유 */}
                <div className="filter-bar" style={{ marginTop: 14, marginBottom: 0 }}>
                  <input
                    type="text" placeholder="GitHub 아이디로 팀원 초대"
                    value={invite[r.id] || ''}
                    onChange={(e) => setInvite((p) => ({ ...p, [r.id]: e.target.value }))}
                    onKeyDown={(e) => e.key === 'Enter' && sendInvite(r.id)}
                  />
                  <button className="btn wh sm" onClick={() => sendInvite(r.id)}>초대</button>
                </div>
              </>
            )}
          </div>
        ))
      )}
    </main>
  );
}
