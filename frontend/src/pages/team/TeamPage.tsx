/*
 * 팀 관리 (/app/team)
 * "팀" = 내가 속한 GitHub 레포(들) — 레포 연동이 곧 팀 생성이고, 팀장이 GitHub 아이디로
 * 특정 사람을 지명해 초대하는 방식만 있다(공유 링크로 아무나 신청받는 기능은 없음).
 * 레포별로 멤버 목록과 초대 입력을 보여준다. `Repos.tsx`의 초대함/초대 패턴을 그대로 재사용.
 */
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/client';
import { useGame } from '../../context/GameContext';
import { PageHead } from '../../components/ui';

export default function TeamPage() {
  const { notify } = useGame();
  const [repos, setRepos] = useState(null); // null = 로딩 중
  const [members, setMembers] = useState({}); // { [repoId]: RepoMemberResponseDTO[] }
  const [inbox, setInbox] = useState([]);     // 받은 대기 중 초대
  const [inviteInput, setInviteInput] = useState({}); // { [repoId]: 입력 중인 GitHub 아이디 }
  const [inviteEmail, setInviteEmail] = useState({}); // { [repoId]: 입력 중인 이메일 }
  const [needsEmail, setNeedsEmail] = useState({});   // { [repoId]: GITHUB_USER_NOT_FOUND라 이메일 입력창 노출 }
  const [inviteError, setInviteError] = useState({}); // { [repoId]: 마지막 초대 실패 메시지 }
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.getMyLinkedRepos().then(setRepos);
    api.getMyInvitations().then(setInbox);
  }, []);

  // 레포 목록이 갱신될 때마다 레포별 멤버를 각각 불러온다
  useEffect(() => {
    if (!repos) return;
    repos.forEach((r) => {
      api.getTeamMembers(r.repoId).then((list) =>
        setMembers((prev) => ({ ...prev, [r.repoId]: list })));
    });
  }, [repos]);

  const respond = async (inv, accept) => {
    await api.respondInvite(inv.repoId, inv.invitationId, accept);
    setInbox((prev) => prev.filter((i) => i.invitationId !== inv.invitationId));
    notify(accept ? `${inv.repoName} 팀에 합류!` : '초대를 거절했어요');
    if (accept) api.getMyLinkedRepos().then(setRepos); // 새로 합류한 레포도 목록에 반영
  };

  // GitHub 아이디로만 먼저 시도 — 우리 시스템이 그 아이디를 모르면(GITHUB_USER_NOT_FOUND, 400)
  // 이메일 입력창을 새로 띄워서, 같은 아이디 + 이메일로 재요청해야 이메일 초대장이 발송된다
  const sendInvite = async (repoId) => {
    const name = (inviteInput[repoId] || '').trim();
    const email = (inviteEmail[repoId] || '').trim();
    if (!name) return;
    if (needsEmail[repoId] && !email) return;
    setBusy(true);
    try {
      await api.inviteMember(repoId, name, needsEmail[repoId] ? email : undefined);
      setInviteInput((p) => ({ ...p, [repoId]: '' }));
      setInviteEmail((p) => ({ ...p, [repoId]: '' }));
      setNeedsEmail((p) => ({ ...p, [repoId]: false }));
      setInviteError((p) => ({ ...p, [repoId]: '' }));
      notify(needsEmail[repoId] ? `${email} 로 초대 메일을 보냈어요` : `@${name} 님에게 초대를 보냈어요`);
    } catch (e) {
      if (e.status === 400 && !needsEmail[repoId]) {
        setNeedsEmail((p) => ({ ...p, [repoId]: true }));
        setInviteError((p) => ({ ...p, [repoId]: e.message }));
      } else {
        notify(e.message || '초대에 실패했어요');
      }
    } finally { setBusy(false); }
  };

  if (!repos) {
    return <main className="app-main"><div className="panel"><p className="note">불러오는 중…</p></div></main>;
  }

  return (
    <main className="app-main">
      <PageHead badge="TEAM" title="팀"
        lead={'내가 속한 GitHub 레포 기준으로 팀을 관리해요.\nGitHub 아이디로 팀원을 지명해서 초대할 수 있어요.'} />

      {inbox.length > 0 && (
        <div className="panel" style={{ marginBottom: 22, background: '#fff6d6' }}>
          <h3>받은 초대 {inbox.length}건</h3>
          {inbox.map((inv) => (
            <div key={inv.invitationId} className="row">
              <span className="mono" style={{ fontSize: 13 }}>{inv.repoName}</span>
              <span className="ml-auto" style={{ display: 'flex', gap: 8 }}>
                <button className="btn co sm" onClick={() => respond(inv, true)}>수락</button>
                <button className="btn wh sm" onClick={() => respond(inv, false)}>거절</button>
              </span>
            </div>
          ))}
        </div>
      )}

      {repos.length === 0 ? (
        <div className="empty">
          <p>아직 속한 팀이 없어요.<br />GitHub 레포를 연동하면 그 레포가 팀이 돼요.</p>
          <Link className="btn co" style={{ marginTop: 16, display: 'inline-block' }} to="/app/repos">
            레포 연동하러 가기
          </Link>
        </div>
      ) : (
        repos.map((r) => (
          <div key={r.repoId} className="panel" style={{ marginBottom: 18 }}>
            <h3>{r.repoName}</h3>
            <table className="tbl">
              <thead><tr><th>GitHub 아이디</th><th>역할</th><th>합류일</th></tr></thead>
              <tbody>
                {(members[r.repoId] || []).map((m) => (
                  <tr key={m.userId}>
                    <td><b>@{m.githubUsername}</b></td>
                    <td><span className={`chip ${m.role === 'OWNER' ? 'co' : 'navy'}`}>{m.role === 'OWNER' ? '팀장' : '팀원'}</span></td>
                    <td className="mono xs">{m.joinedAt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="filter-bar" style={{ marginTop: 14, marginBottom: 0 }}>
              <input
                type="text" placeholder="GitHub 아이디로 팀원 초대"
                value={inviteInput[r.repoId] || ''}
                onChange={(e) => {
                  setInviteInput((p) => ({ ...p, [r.repoId]: e.target.value }));
                  setNeedsEmail((p) => ({ ...p, [r.repoId]: false })); // 아이디를 바꾸면 이전 실패 상태는 초기화
                  setInviteError((p) => ({ ...p, [r.repoId]: '' }));
                }}
                onKeyDown={(e) => e.key === 'Enter' && !needsEmail[r.repoId] && sendInvite(r.repoId)}
              />
              <button
                className="btn wh sm"
                disabled={busy || needsEmail[r.repoId] || !(inviteInput[r.repoId] || '').trim()}
                onClick={() => sendInvite(r.repoId)}
              >초대</button>
            </div>

            {needsEmail[r.repoId] && (
              <div className="filter-bar" style={{ marginTop: 8, marginBottom: 0 }}>
                <input
                  type="email" placeholder="이메일로 초대장 보내기 (예: friend@mail.com)"
                  value={inviteEmail[r.repoId] || ''}
                  onChange={(e) => setInviteEmail((p) => ({ ...p, [r.repoId]: e.target.value }))}
                  onKeyDown={(e) => e.key === 'Enter' && sendInvite(r.repoId)}
                />
                <button
                  className="btn co sm"
                  disabled={busy || !(inviteEmail[r.repoId] || '').includes('@')}
                  onClick={() => sendInvite(r.repoId)}
                >메일 발송</button>
              </div>
            )}
            {inviteError[r.repoId] && (
              <p className="note sm" style={{ marginTop: 6, color: 'var(--coral)' }}>{inviteError[r.repoId]}</p>
            )}
          </div>
        ))
      )}
    </main>
  );
}
