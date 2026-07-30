/*
 * 모바일 전용 성장 추이.
 * 데스크톱은 720×250 SVG 곡선인데 폰에서 그대로 줄이면 선도 라벨도 안 읽힌다.
 * 필터 3개를 한 줄씩 세우고, 곡선 대신 주별 막대(발생·해결)로 바꾼다.
 * 팀장 겹쳐보기도 멀티라인 대신 팀원별 막대 카드로 편다 — 폰에서 선이 겹치면 누가 누군지 모른다.
 * 기능은 데스크톱과 같다 — 레포·팀원·기간 필터, 총합 추이, 팀원 겹쳐보기, 요약 3종.
 */
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import * as api from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useGame } from "../../context/GameContext";
import "../../styles/mobile/growth.css";

const COMPARE_COLORS = ["#ff6b57", "#4ec9a4", "#1b2a4a", "#c9862e", "#8b96b5", "#e06fae"];

// 12주면 라벨이 겹친다. 몇 칸 걸러 하나씩만 쓰고 마지막 주는 항상 보여준다
const labelEvery = (n) => Math.ceil(n / 6);

export default function MobileGrowth() {
  const { S } = useGame();
  const { user } = useAuth();

  const [trend, setTrend] = useState(null);
  const [compare, setCompare] = useState(null);
  const [period, setPeriod] = useState("4W");
  const [memberId, setMemberId] = useState("ALL");
  const [members, setMembers] = useState([]);
  const [repos, setRepos] = useState([]);
  const [repoId, setRepoId] = useState(null);

  useEffect(() => {
    api.getMyLinkedRepos().then((list) => {
      setRepos(list);
      if (list.length > 0) setRepoId(list[0].repoId);
    });
  }, []);

  // 레포가 바뀌면 팀원 필터는 '팀 전체'로 되돌린다
  useEffect(() => {
    if (repoId == null) return;
    setMemberId("ALL");
    api.getTeamMembers(repoId).then(setMembers);
  }, [repoId]);

  const isMe = (m) =>
    (user?.userId != null && String(m.userId) === String(user.userId)) ||
    (!!user?.githubUsername && m.githubUsername === user.githubUsername);
  const myRole = members.find(isMe)?.role;
  // 팀장만 전원 조회. 팀원은 본인만 골라진다
  const selectableMembers = myRole === "OWNER" ? members : members.filter(isMe);
  const compareMode = memberId === "ALL" && myRole === "OWNER";

  useEffect(() => {
    if (repoId == null) return;
    api.getGrowthTrend(repoId, { period, ...(memberId !== "ALL" && { memberId }) }).then(setTrend);
    if (compareMode) api.getGrowthCompare(repoId, { period }).then(setCompare);
    else setCompare(null);
  }, [repoId, period, memberId, compareMode]);

  const total = (trend || []).reduce((a, w) => a + w.issueCount, 0);
  const solved = (trend || []).reduce((a, w) => a + w.resolved, 0);
  const first = trend?.[0]?.issueCount ?? 0;
  const last = trend?.[trend.length - 1]?.issueCount ?? 0;
  const improving = last < first;
  const dropPct = first > 0 && last < first ? Math.round((1 - last / first) * 100) : 0;

  return (
    <main className="mapp">
      <p className="mlead">반복하던 실수가 줄어드는 게 진짜 성장이에요.</p>

      {/* 필터 — 데스크톱은 가로 3개, 폰은 한 줄씩 */}
      <section className="mcard mg-filter">
        {repos.length > 0 && (
          <select className="msel" value={repoId ?? ""} onChange={(e) => setRepoId(Number(e.target.value))}>
            {repos.map((r) => <option key={r.repoId} value={r.repoId}>{r.repoName}</option>)}
          </select>
        )}
        <select className="msel" value={memberId} onChange={(e) => setMemberId(e.target.value)}>
          <option value="ALL">팀 전체</option>
          {selectableMembers.map((m) => (
            <option key={m.userId} value={m.userId}>{m.githubUsername ? `@${m.githubUsername}` : (m.nickname || '탈퇴한 회원')}{isMe(m) ? " (나)" : ""}</option>
          ))}
        </select>
        <div className="mseg mg-period">
          {[["4W", "4주"], ["8W", "8주"], ["12W", "12주"]].map(([v, ko]) => (
            <button key={v} className={period === v ? "on" : ""} onClick={() => setPeriod(v)}>{ko}</button>
          ))}
        </div>
      </section>

      {repos.length === 0 ? (
        <section className="mcard mempty"><p>아직 성장 추이가 없어요.<br />GitHub 레포를 연동하고 PR 리뷰를 받으면 주별로 쌓여요.</p></section>
      ) : !trend ? (
        <p className="mnote">집계 중…</p>
      ) : trend.length === 0 ? (
        <section className="mcard mempty"><p>아직 집계할 데이터가 없어요.<br />PR 리뷰가 쌓이면 주간 발생·해결 추이가 그려져요.</p></section>
      ) : (
        <>
          <section className="mcard">
            <div className="mcard-head">
              <h2>주간 이슈 빈도</h2>
              {dropPct > 0 && <span className="mg-drop">▼ {dropPct}% 감소중</span>}
            </div>
            <Bars rows={trend.map((w) => ({ label: w.label, open: w.issueCount, ok: w.resolved }))} />
            <div className="mg-legend">
              <span><i className="c-open" />발생 (줄수록 성장)</span>
              <span><i className="c-ok" />해결 (늘수록 성장)</span>
            </div>
          </section>

          {/* 팀장이 팀 전체를 볼 때만 — 팀원별로 카드를 따로 준다 */}
          {compareMode && compare && (
            <section className="mcard">
              <div className="mcard-head"><h2>팀원별 발생</h2></div>
              <p className="mnote mg-sub">막대가 낮아질수록 그 팀원의 실수가 줄어드는 거예요.</p>
              {compare.series.map((s, si) => {
                const color = COMPARE_COLORS[si % COMPARE_COLORS.length];
                const sum = s.issues.reduce((a, v) => a + v, 0);
                return (
                  <div key={s.member} className="mg-member">
                    <div className="mg-mtop">
                      <i style={{ background: color }} />
                      <b>{s.member}</b>
                      <span>{sum}건</span>
                    </div>
                    <Bars
                      rows={compare.labels.map((lb, i) => ({ label: lb, open: s.issues[i] ?? 0 }))}
                      color={color}
                    />
                  </div>
                );
              })}
            </section>
          )}

          <div className="mg-stats">
            <div className="mcard"><b>{total}</b><i>기간 내 이슈</i></div>
            <div className="mcard"><b className="ok">{solved}</b><i>해결 완료</i></div>
            <div className="mcard"><b className="co">{S.totalDays}</b><i>총 학습일</i></div>
          </div>

          <section className="mcard">
            <div className="mcard-head"><h2>{improving ? "📉 좋은 신호예요" : "📈 조금 더 힘내요"}</h2></div>
            <p className="mnote">
              {improving
                ? `첫 주 ${first}건 → 마지막 주 ${last}건. 반복 실수가 확실히 줄고 있어요.`
                : "이슈가 아직 줄지 않았어요. 약점 카드 퀴즈를 하루 1회만 제출해도 흐름이 바뀝니다."}
              <br />
              지난 리포트는 <Link to="/app/reports">주간 리포트</Link>에서 볼 수 있어요.
            </p>
          </section>
        </>
      )}
    </main>
  );
}

/* 주별 막대. ok가 없으면 한 줄 막대(팀원별 카드), 있으면 발생·해결 두 줄 */
function Bars({ rows, color }: { rows: { label: string; open: number; ok?: number }[]; color?: string }) {
  const max = Math.max(1, ...rows.map((r) => Math.max(r.open, r.ok ?? 0)));
  const step = labelEvery(rows.length);

  return (
    <div className={`mg-bars ${color ? "mini" : ""}`}>
      {rows.map((r, i) => (
        <div key={i} className="mg-bar">
          <span className="mg-plot">
            <i className="c-open" style={{ height: `${(r.open / max) * 100}%`, ...(color && { background: color }) }} />
            {r.ok != null && <i className="c-ok" style={{ height: `${(r.ok / max) * 100}%` }} />}
          </span>
          <b>{r.open}</b>
          {/* 라벨이 겹치지 않게 몇 칸 걸러 하나씩 (마지막 주는 항상) */}
          <em>{i % step === 0 || i === rows.length - 1 ? r.label : ""}</em>
        </div>
      ))}
    </div>
  );
}
