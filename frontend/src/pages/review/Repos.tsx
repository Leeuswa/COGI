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
  const [linked, setLinked] = useState({}); // { [githubRepoId]: 내부 repoId } — 이미 연동된 레포만 들어있음
  const [invite, setInvite] = useState({}); // githubRepoId → 입력 중인 GitHub 아이디
  const [inviteEmail, setInviteEmail] = useState({}); // githubRepoId → 입력 중인 이메일
  const [needsEmail, setNeedsEmail] = useState({});   // githubRepoId → GITHUB_USER_NOT_FOUND라 이메일 입력창 노출
  const [inviteError, setInviteError] = useState({}); // githubRepoId → 마지막 초대 실패 메시지
  const [busy, setBusy] = useState(false);
  const [inbox, setInbox] = useState([]);   // 내가 받은 대기 중 초대 (FR-37)

  useEffect(() => {
    if (user.githubUsername) api.getGithubRepos().then(setRepos);
    api.getMyInvitations().then(setInbox);
    // 이미 연동해둔 레포 목록 — GET /api/repos/github엔 "연동 여부"가 없어서 따로 대조해야 함
    api.getMyLinkedRepos().then((rows) =>
      setLinked(Object.fromEntries(rows.map((r) => [r.githubRepoId, r.repoId]))));
  }, [user.githubUsername]);

  // 초대 수락/거절 (API-030/030-1). 수락하면 그 레포 PR 목록이 열린다
  const respond = async (inv, accept) => {
    await api.respondInvite(inv.repoId, inv.invitationId, accept);
    setInbox((prev) => prev.filter((i) => i.invitationId !== inv.invitationId));
    notify(accept ? `${inv.repoName} 팀에 합류!` : '초대를 거절했어요');
  };

  const link = async (repo) => {
    const res = await api.linkRepo(repo.githubRepoId);
    setLinked((prev) => ({ ...prev, [repo.githubRepoId]: res.repoId }));
    notify(`${repo.repoName} 연동 완료!`);
  };

  // GitHub 아이디로만 먼저 시도 — 우리 시스템이 모르는 아이디면(GITHUB_USER_NOT_FOUND, 400)
  // 이메일 입력창을 새로 띄워서, 같은 아이디 + 이메일로 재요청해야 이메일 초대장이 발송된다
  const sendInvite = async (githubRepoId) => {
    const name = (invite[githubRepoId] || '').trim();
    const email = (inviteEmail[githubRepoId] || '').trim();
    if (!name) return;
    if (needsEmail[githubRepoId] && !email) return;
    setBusy(true);
    try {
      await api.inviteMember(linked[githubRepoId], name, needsEmail[githubRepoId] ? email : undefined);
      setInvite((p) => ({ ...p, [githubRepoId]: '' }));
      setInviteEmail((p) => ({ ...p, [githubRepoId]: '' }));
      setNeedsEmail((p) => ({ ...p, [githubRepoId]: false }));
      setInviteError((p) => ({ ...p, [githubRepoId]: '' }));
      notify(needsEmail[githubRepoId] ? `${email} 로 초대 메일을 보냈어요` : `@${name} 초대장 발송 (수락 대기)`);
    } catch (e) {
      if (e.status === 400 && !needsEmail[githubRepoId]) {
        setNeedsEmail((p) => ({ ...p, [githubRepoId]: true }));
        setInviteError((p) => ({ ...p, [githubRepoId]: e.message }));
      } else {
        notify(e.message || '초대에 실패했어요');
      }
    } finally { setBusy(false); }
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
            <div key={inv.invitationId} className="row">
              <span className="mono" style={{ fontSize: 13 }}>{inv.repoName}</span>
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
        repos.map((r) => {
          const repoId = linked[r.githubRepoId]; // 연동 안 됐으면 undefined
          return (
          <div key={r.githubRepoId} className="panel">
            <div className="row">
              <b style={{ fontSize: 15 }}>{r.repoName}</b>
              <span className={`chip ${r.isPrivate ? 'navy' : 'gray'}`}>{r.isPrivate ? 'PRIVATE' : 'PUBLIC'}</span>
              {repoId
                ? <span className="chip low">LINKED</span>
                : <span className="chip gray">NOT LINKED</span>}
              <div className="ml-auto">
                {repoId
                  ? <Link className="btn wh sm" to="/app/prs">PR 목록 보기 →</Link>
                  : <button className="btn co sm" onClick={() => link(r)}>연동하기</button>}
              </div>
            </div>

            {repoId && (
              <>
                {/* 팀원 초대 (FR-36): GitHub 아이디로 초대 → 수락하면 팀 대시보드 공유 */}
                <div className="filter-bar" style={{ marginTop: 14, marginBottom: 0 }}>
                  <input
                    type="text" placeholder="GitHub 아이디로 팀원 초대"
                    value={invite[r.githubRepoId] || ''}
                    onChange={(e) => {
                      setInvite((p) => ({ ...p, [r.githubRepoId]: e.target.value }));
                      setNeedsEmail((p) => ({ ...p, [r.githubRepoId]: false }));
                      setInviteError((p) => ({ ...p, [r.githubRepoId]: '' }));
                    }}
                    onKeyDown={(e) => e.key === 'Enter' && !needsEmail[r.githubRepoId] && sendInvite(r.githubRepoId)}
                  />
                  <button
                    className="btn wh sm"
                    disabled={busy || needsEmail[r.githubRepoId] || !(invite[r.githubRepoId] || '').trim()}
                    onClick={() => sendInvite(r.githubRepoId)}
                  >초대</button>
                </div>
                {needsEmail[r.githubRepoId] && (
                  <div className="filter-bar" style={{ marginTop: 8, marginBottom: 0 }}>
                    <input
                      type="email" placeholder="이메일로 초대장 보내기 (예: friend@mail.com)"
                      value={inviteEmail[r.githubRepoId] || ''}
                      onChange={(e) => setInviteEmail((p) => ({ ...p, [r.githubRepoId]: e.target.value }))}
                      onKeyDown={(e) => e.key === 'Enter' && sendInvite(r.githubRepoId)}
                    />
                    <button
                      className="btn co sm"
                      disabled={busy || !(inviteEmail[r.githubRepoId] || '').includes('@')}
                      onClick={() => sendInvite(r.githubRepoId)}
                    >메일 발송</button>
                  </div>
                )}
                {inviteError[r.githubRepoId] && (
                  <p className="note sm" style={{ marginTop: 6, color: 'var(--coral)' }}>{inviteError[r.githubRepoId]}</p>
                )}
              </>
            )}
          </div>
          );
        })
      )}
    </main>
  );
}
