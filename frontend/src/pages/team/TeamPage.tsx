/*
 * 팀 관리 (/app/team)
 * "팀" = 내가 속한 GitHub 레포(들) — 레포 연동이 곧 팀 생성이고, 팀장이 GitHub 아이디로
 * 특정 사람을 지명해 초대하는 방식만 있다(공유 링크로 아무나 신청받는 기능은 없음).
 * 레포별로 멤버 목록과 초대 입력을 보여준다. `Repos.tsx`의 초대함/초대 패턴을 그대로 재사용.
 */
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { useGame } from '../../context/GameContext';
import { PageHead } from '../../components/ui';
import useIsMobile from '../../hooks/useIsMobile';
import MobileTeam from '../mobile/MobileTeam';

// 서버가 LocalDateTime을 그대로 내려줘 "2026-07-27T17:16:13.186451"이 표에 박혔다
const joined = (iso: string) => (iso ? iso.slice(0, 10).replace(/-/g, '.') : '-');
// GitHub 미연동·탈퇴 회원은 @만 덩그러니 남는다. 닉네임으로 받아주고, 그것도 없으면 탈퇴로 본다
const handle = (m) => (m.githubUsername ? `@${m.githubUsername}` : (m.nickname || '탈퇴한 회원'));

// 폰이면 데스크톱 본체를 아예 mount하지 않는다.
// 한 컴포넌트 안에서 갈랐더니 조건부 return 위의 useEffect가 그대로 돌아 같은 API를 두 번 때렸다.
// getWeaknessStats처럼 조회할 때마다 통계를 지우고 다시 넣는 API는 두 번 겹치면 한쪽이 빈 목록을 받는다.
export default function TeamPage() {
  return useIsMobile() ? <MobileTeam /> : <DesktopTeamPage />;
}

function DesktopTeamPage() {
  const { user } = useAuth();
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

  // 팀원이 스스로 나가기 — 팀장이면 백엔드가 OWNER_CANNOT_LEAVE로 막아줌(먼저 위임하라고 안내)
  const leave = async (repoId) => {
    if (!window.confirm('정말 이 팀에서 나갈까요?')) return;
    try {
      await api.leaveRepo(repoId);
      notify('팀에서 나갔어요');
      setRepos((prev) => prev.filter((r) => r.repoId !== repoId));
    } catch (e) { notify(e.message || '나가기에 실패했어요'); }
  };

  // 팀장이 팀원 내보내기
  const kick = async (repoId, m) => {
    if (!window.confirm(`@${m.githubUsername} 님을 팀에서 내보낼까요?`)) return;
    try {
      await api.removeTeamMember(repoId, m.userId);
      setMembers((prev) => ({ ...prev, [repoId]: prev[repoId].filter((x) => x.userId !== m.userId) }));
      notify(`@${m.githubUsername} 님을 내보냈어요`);
    } catch (e) { notify(e.message || '내보내기에 실패했어요'); }
  };

  // 팀장 위임 — 위임하면 내 역할도 MEMBER로 바뀐다
  const transfer = async (repoId, m) => {
    if (!window.confirm(`@${m.githubUsername} 님에게 팀장을 위임할까요?\n위임하면 회원님은 팀원이 돼요.`)) return;
    try {
      await api.transferOwnership(repoId, m.userId);
      setMembers((prev) => ({
        ...prev,
        [repoId]: prev[repoId].map((x) => ({
          ...x,
          role: x.userId === m.userId ? 'OWNER' : x.githubUsername === user.githubUsername ? 'MEMBER' : x.role,
        })),
      }));
      notify(`@${m.githubUsername} 님에게 팀장을 위임했어요`);
    } catch (e) { notify(e.message || '위임에 실패했어요'); }
  };

  if (!repos) {
    return <main className="app-main"><div className="panel"><p className="note">불러오는 중…</p></div></main>;
  }


  return (
    <main className="app-main">
      {/* 버튼을 안내 문구 아래 별도 줄에 두니 그만큼 목록이 통째로 밀려 내려갔다. 같은 줄 오른쪽으로 */}
      <div className="tm-top">
        <PageHead badge="TEAM" title="팀"
          lead={'내가 속한 GitHub 레포 기준으로 팀을 관리해요.\nGitHub 아이디로 팀원을 지명해서 초대할 수 있어요.'} />
        {repos.length > 0 && (
          <Link className="btn co sm" to="/app/repos">+ 새 팀 만들기</Link>
        )}
      </div>

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
        repos.map((r) => {
          // userId로 본다 — GitHub 미연동끼리는 githubUsername이 둘 다 null이라 남을 나로 착각한다
          const mine = (m) => String(m.userId) === String(user.userId);
          const myRole = (members[r.repoId] || []).find(mine)?.role;
          return (
          <div key={r.repoId} className="panel" style={{ marginBottom: 18 }}>
            <h3>{r.repoName}</h3>
            <table className="tbl">
              <thead><tr><th>GitHub 아이디</th><th>역할</th><th>합류일</th><th></th></tr></thead>
              <tbody>
                {(members[r.repoId] || []).map((m) => {
                  const isMe = mine(m);
                  return (
                    <tr key={m.userId}>
                      <td><b>{handle(m)}{isMe && <span className="chip low tm-me">나</span>}</b></td>
                      <td><span className={`chip ${m.role === 'OWNER' ? 'co' : 'navy'}`}>{m.role === 'OWNER' ? '팀장' : '팀원'}</span></td>
                      <td className="mono xs">{joined(m.joinedAt)}</td>
                      <td>
                        <div className="row tm-acts">
                          {isMe && m.role === 'MEMBER' && (
                            <button className="btn wh sm" onClick={() => leave(r.repoId)}>나가기</button>
                          )}
                          {!isMe && myRole === 'OWNER' && m.role === 'MEMBER' && (
                            <>
                              <button className="btn wh sm" onClick={() => transfer(r.repoId, m)}>위임</button>
                              <button className="btn wh sm" onClick={() => kick(r.repoId, m)}>내보내기</button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
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
          );
        })
      )}
    </main>
  );
}
