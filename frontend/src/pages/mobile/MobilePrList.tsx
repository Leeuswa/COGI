/*
 * 모바일 전용 PR 리뷰 목록.
 * 데스크톱은 7열 표인데 폰에서는 옆으로 잘린다. 표를 버리고 카드 리스트로 바꿨다.
 * 카드 한 장에 PR 하나 — 번호·제목·작성자·이슈 수·심각도를 두 줄에 담는다.
 */
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import * as api from "../../api/client";
import { SEVERITIES, sevKo } from "../../data/constants";
import "../../styles/mobile/pr-list.css";

export default function MobilePrList() {
  const [repos, setRepos] = useState(null);
  const [repoId, setRepoId] = useState(null);
  const [prs, setPrs] = useState(null); // 서버가 이미 걸러서 내려준, 화면에 실제로 보여줄 목록
  const [authors, setAuthors] = useState<string[]>([]); // 담당자 필터 옵션(FR-42) — 필터 전 전체 목록에서 뽑음
  const [hasAnyPr, setHasAnyPr] = useState(true);
  const [sev, setSev] = useState("ALL");
  const [author, setAuthor] = useState("ALL"); // 담당자 필터 (FR-42) — 데스크톱과 같은 기능

  useEffect(() => {
    api.getMyLinkedRepos().then((list) => {
      setRepos(list);
      if (list.length > 0) setRepoId(list[0].repoId);
    });
  }, []);

  useEffect(() => {
    if (repoId == null) return;
    setSev("ALL");
    setAuthor("ALL");
    api.getRepoReviewedPrs(repoId).then((list) => {
      setAuthors([...new Set<string>(list.map((p) => p.authorName).filter(Boolean))]);
      setHasAnyPr(list.length > 0);
    });
  }, [repoId]);

  useEffect(() => {
    if (repoId == null) return;
    setPrs(null);
    api.getRepoReviewedPrs(repoId, { severity: sev, author }).then(setPrs);
  }, [repoId, sev, author]);

  const shown = prs || [];

  if (repos && repos.length === 0) {
    return (
      <main className="mapp">
        <section className="mcard mempty">
          <p>연동된 레포가 없어요.<br />먼저 레포를 연동해 주세요.</p>
          <Link className="btn co sm full" to="/app/repos">레포 연동하기</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="mapp">
      {/* 레포 선택 — 여러 개일 때만 */}
      {repos && repos.length > 1 && (
        <select className="msel" value={repoId ?? ""} onChange={(e) => setRepoId(Number(e.target.value))}>
          {repos.map((r) => <option key={r.repoId} value={r.repoId}>{r.repoName}</option>)}
        </select>
      )}

      {/* 심각도 — 드롭다운 대신 앱에서 흔한 세그먼트 버튼 */}
      <div className="mseg">
        <button className={sev === "ALL" ? "on" : ""} onClick={() => setSev("ALL")}>전체</button>
        {SEVERITIES.map((s) => (
          <button key={s} className={sev === s ? "on" : ""} onClick={() => setSev(s)}>{sevKo(s)}</button>
        ))}
      </div>

      {/* 담당자 — 두 명 이상일 때만 띄운다 */}
      {authors.length > 1 && (
        <select className="msel" value={author} onChange={(e) => setAuthor(e.target.value)}>
          <option value="ALL">담당자 전체</option>
          {authors.map((a) => <option key={a}>{a}</option>)}
        </select>
      )}

      {!prs ? (
        <p className="mnote">불러오는 중…</p>
      ) : shown.length === 0 ? (
        <section className="mcard mempty">
          <p>{!hasAnyPr ? "아직 리뷰한 PR이 없어요." : "조건에 맞는 PR이 없어요."}</p>
          <Link className="btn wh sm full" to="/app/paste">리뷰 스튜디오 열기</Link>
        </section>
      ) : (
        <ul className="mpr">
          {shown.map((pr) => (
            <li key={pr.id}>
              <Link to={`/app/prs/${pr.id}`}>
                <div className="mpr-top">
                  <span className="mpr-num">#{pr.githubPrNumber}</span>
                  {pr.topSeverity && <span className={`mpr-sev ${pr.topSeverity.toLowerCase()}`}>{sevKo(pr.topSeverity)}</span>}
                  <span className="mpr-go">›</span>
                </div>
                <b className="mpr-title">{pr.title}</b>
                <div className="mpr-meta">
                  <span>{pr.authorName ?? "작성자 없음"}</span>
                  <span className="dot">·</span>
                  <span>이슈 {pr.issueCount}건</span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}

      <Link className="btn co sm full mpr-cta" to="/app/paste">리뷰 스튜디오 열기</Link>
    </main>
  );
}
