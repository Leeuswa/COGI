/*
 * 모바일 전용 팀 화면.
 * 데스크톱은 레포마다 3열 표를 그리는데 폰에서는 옆으로 잘린다. 표만 카드 리스트로 바꿨다.
 * 기능은 데스크톱과 똑같이 다 있다 —
 *   새 팀 만들기 · 초대 수락/거절 · GitHub 아이디 초대 · (없는 계정이면) 이메일 초대 폴백
 *   팀장: 위임 · 내보내기 / 팀원: 나가기
 */
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import * as api from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useGame } from "../../context/GameContext";
import "../../styles/mobile/team.css";

// "2026-07-27T17:16:13.186451" → "2026.07.27"
const ymd = (v: string) => (v ? v.slice(0, 10).replace(/-/g, ".") : "-");

export default function MobileTeam() {
  const { user } = useAuth();
  const { notify } = useGame();

  const [repos, setRepos] = useState(null);
  const [members, setMembers] = useState({});
  const [inbox, setInbox] = useState([]);
  const [inviteInput, setInviteInput] = useState({});
  const [inviteEmail, setInviteEmail] = useState({});
  const [needsEmail, setNeedsEmail] = useState({}); // GITHUB_USER_NOT_FOUND면 이메일 입력창을 연다
  const [inviteError, setInviteError] = useState({});
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.getMyLinkedRepos().then(setRepos).catch(() => setRepos([]));
    api.getMyInvitations().then(setInbox).catch(() => {});
  }, []);

  useEffect(() => {
    (repos || []).forEach((r) => {
      api.getTeamMembers(r.repoId)
        .then((list) => setMembers((p) => ({ ...p, [r.repoId]: list })))
        .catch(() => {});
    });
  }, [repos]);

  const reload = (repoId) =>
    api.getTeamMembers(repoId).then((list) => setMembers((p) => ({ ...p, [repoId]: list })));

  const respond = async (inv, accept) => {
    await api.respondInvite(inv.repoId, inv.invitationId, accept);
    setInbox((p) => p.filter((x) => x.invitationId !== inv.invitationId));
    if (accept) api.getMyLinkedRepos().then(setRepos);
  };

  // GitHub 아이디로 초대. 그 아이디가 COGI에 없으면 이메일 입력창을 열어 메일로 보낸다
  const sendInvite = async (repoId) => {
    const name = (inviteInput[repoId] || "").trim();
    const email = (inviteEmail[repoId] || "").trim();
    if (!name || busy) return;
    if (needsEmail[repoId] && !email) return;
    setBusy(true);
    try {
      await api.inviteMember(repoId, name, needsEmail[repoId] ? email : undefined);
      setInviteInput((p) => ({ ...p, [repoId]: "" }));
      setInviteEmail((p) => ({ ...p, [repoId]: "" }));
      setNeedsEmail((p) => ({ ...p, [repoId]: false }));
      setInviteError((p) => ({ ...p, [repoId]: "" }));
      notify(needsEmail[repoId] ? `${email} 로 초대 메일을 보냈어요` : `@${name} 님에게 초대를 보냈어요`);
      reload(repoId);
    } catch (e) {
      if (e.status === 400 && !needsEmail[repoId]) {
        setNeedsEmail((p) => ({ ...p, [repoId]: true }));
        setInviteError((p) => ({ ...p, [repoId]: "COGI에 없는 아이디예요. 이메일로 초대할게요." }));
      } else {
        setInviteError((p) => ({ ...p, [repoId]: e.message || "초대에 실패했어요" }));
      }
    } finally { setBusy(false); }
  };

  const leave = async (repoId) => {
    if (!window.confirm("이 팀에서 나갈까요?")) return;
    setBusy(true);
    try {
      await api.leaveRepo(repoId);
      setRepos((p) => p.filter((r) => r.repoId !== repoId));
      notify("팀에서 나왔어요");
    } finally { setBusy(false); }
  };

  const kick = async (repoId, m) => {
    if (!window.confirm(`@${m.githubUsername} 님을 팀에서 내보낼까요?`)) return;
    setBusy(true);
    try {
      await api.removeTeamMember(repoId, m.userId);
      notify(`@${m.githubUsername} 님을 내보냈어요`);
      reload(repoId);
    } finally { setBusy(false); }
  };

  const transfer = async (repoId, m) => {
    if (!window.confirm(`@${m.githubUsername} 님에게 팀장을 위임할까요?\n위임하면 회원님은 팀원이 돼요.`)) return;
    setBusy(true);
    try {
      await api.transferOwnership(repoId, m.userId);
      notify(`@${m.githubUsername} 님이 팀장이 됐어요`);
      reload(repoId);
    } finally { setBusy(false); }
  };

  if (!repos) return <main className="mapp"><p className="mnote">불러오는 중…</p></main>;

  return (
    <main className="mapp">
      <Link className="btn co sm full" to="/app/repos">+ 새 팀 만들기</Link>

      {/* 받은 초대 — 있을 때만 맨 위에 */}
      {inbox.length > 0 && (
        <section className="mcard minvite">
          <div className="mcard-head"><h2>받은 초대</h2></div>
          {inbox.map((inv) => (
            <div key={inv.invitationId} className="mi-row">
              <b>{inv.repoName}</b>
              <div className="mi-btns">
                <button className="btn co sm" onClick={() => respond(inv, true)}>수락</button>
                <button className="btn wh sm" onClick={() => respond(inv, false)}>거절</button>
              </div>
            </div>
          ))}
        </section>
      )}

      {repos.length === 0 ? (
        <section className="mcard mempty">
          <p>아직 속한 팀이 없어요.<br />레포를 연동하면 팀이 만들어집니다.</p>
          <Link className="btn co sm full" to="/app/repos">레포 연동하기</Link>
        </section>
      ) : (
        repos.map((r) => {
          const list = members[r.repoId] || [];
          // userId로 본다 — GitHub 미연동끼리는 githubUsername이 둘 다 null이라 남을 나로 착각한다
          const mine = (m) => String(m.userId) === String(user.userId);
          const myRole = list.find(mine)?.role;
          const isOwner = myRole === "OWNER";
          return (
            <section key={r.repoId} className="mcard">
              <div className="mcard-head">
                <h2>{r.repoName}</h2>
                <span className="mnote">{list.length}명</span>
              </div>

              <ul className="mteam">
                {list.map((m) => {
                  const isMe = mine(m);
                  return (
                    <li key={m.userId}>
                      <span className={`mt-role ${m.role === "OWNER" ? "own" : ""}`}>
                        {m.role === "OWNER" ? "팀장" : "팀원"}
                      </span>
                      <span className="mt-who">
                        <b>@{m.githubUsername ?? "미연동"}{isMe && " (나)"}</b>
                        <i>{ymd(m.joinedAt)} 합류</i>
                      </span>
                      {/* 나면 나가기, 팀장이 남을 볼 때는 위임·내보내기 */}
                      {isMe && !isOwner && (
                        <button className="btn wh sm mt-act" disabled={busy} onClick={() => leave(r.repoId)}>나가기</button>
                      )}
                      {isOwner && !isMe && (
                        <span className="mt-acts">
                          <button className="btn wh sm" disabled={busy} onClick={() => transfer(r.repoId, m)}>위임</button>
                          <button className="btn wh sm" disabled={busy} onClick={() => kick(r.repoId, m)}>내보내기</button>
                        </span>
                      )}
                    </li>
                  );
                })}
              </ul>

              {/* 팀장만 초대할 수 있다 */}
              {isOwner && (
                <div className="mt-invite">
                  <div className="mt-inv-row">
                    <input
                      value={inviteInput[r.repoId] || ""}
                      onChange={(e) => setInviteInput((p) => ({ ...p, [r.repoId]: e.target.value }))}
                      onKeyDown={(e) => e.key === "Enter" && !needsEmail[r.repoId] && sendInvite(r.repoId)}
                      placeholder="GitHub 아이디로 초대"
                    />
                    {!needsEmail[r.repoId] && (
                      <button className="btn co sm" disabled={busy || !(inviteInput[r.repoId] || "").trim()}
                        onClick={() => sendInvite(r.repoId)}>초대</button>
                    )}
                  </div>

                  {/* COGI에 없는 아이디면 이메일로 보낸다 */}
                  {needsEmail[r.repoId] && (
                    <div className="mt-inv-row">
                      <input type="email"
                        value={inviteEmail[r.repoId] || ""}
                        onChange={(e) => setInviteEmail((p) => ({ ...p, [r.repoId]: e.target.value }))}
                        onKeyDown={(e) => e.key === "Enter" && sendInvite(r.repoId)}
                        placeholder="초대 메일 받을 주소"
                      />
                      <button className="btn co sm" disabled={busy || !(inviteEmail[r.repoId] || "").trim()}
                        onClick={() => sendInvite(r.repoId)}>메일 보내기</button>
                    </div>
                  )}

                  {inviteError[r.repoId] && <p className="mt-err">{inviteError[r.repoId]}</p>}
                </div>
              )}
            </section>
          );
        })
      )}
    </main>
  );
}
