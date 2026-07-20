/*
 * PR 리뷰 현황 (RDB-002, API-032, 필터 FR-41~42)
 * 원래 명세는 "팀 PR 목록"이었지만 백엔드에 팀 개념이 따로 없어(레포+팀원이 곧 팀) 레포 기준으로 축소.
 * 여러 레포를 연동했다면 먼저 레포를 고른다. 심각도/담당자 필터는 받아온 목록을 클라이언트에서 거른다
 * (레포당 PR 수가 적어 서버 필터링까지는 아직 안 만듦).
 */
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/client';
import { PageHead, SevChip } from '../../components/ui';
import { SEVERITIES, sevKo, PROCESS_STATUS_KO } from '../../data/constants';
import { SPR } from '../../assets/sprites';

export default function PrList() {
  const [repos, setRepos] = useState(null);
  const [repoId, setRepoId] = useState(null);
  const [prs, setPrs] = useState(null);
  const [f, setF] = useState({ severity: 'ALL', author: 'ALL' });

  useEffect(() => {
    api.getMyLinkedRepos().then((list) => {
      setRepos(list);
      if (list.length > 0) setRepoId(list[0].repoId); // 기본: 첫 레포
    });
  }, []);

  useEffect(() => {
    if (repoId == null) return;
    setPrs(null);
    api.getRepoReviewedPrs(repoId).then(setPrs);
  }, [repoId]);

  const set = (k) => (e) => setF((p) => ({ ...p, [k]: e.target.value }));

  const shown = (prs || []).filter((pr) => {
    if (f.severity !== 'ALL' && pr.topSeverity !== f.severity) return false;
    if (f.author !== 'ALL' && pr.authorName !== f.author) return false;
    return true;
  });
  const authors: string[] = [...new Set<string>((prs || []).map((p) => p.authorName).filter(Boolean))]; // 담당자 필터 옵션 (FR-42)

  return (
    <main className="app-main">
      <PageHead badge="PULL REQUESTS" title="PR 리뷰 현황" lead="Studio에서 PR을 가져와 리뷰하면 여기 쌓여요." />

      {repos && repos.length === 0 ? (
        <div className="empty">
          <img src={SPR.happy} alt="" />
          <p>연동된 레포가 없어요. 먼저 레포를 연동해보세요.</p>
          <Link className="btn wh sm" style={{ marginTop: 12, display: 'inline-block' }} to="/app/repos">레포 연동 →</Link>
        </div>
      ) : (
        <>
          <div className="filter-bar">
            {repos && repos.length > 1 && (
              <select value={repoId ?? ''} onChange={(e) => setRepoId(Number(e.target.value))}>
                {repos.map((r) => <option key={r.repoId} value={r.repoId}>{r.repoName}</option>)}
              </select>
            )}
            <select value={f.severity} onChange={set('severity')}>
              <option value="ALL">심각도 전체</option>
              {SEVERITIES.map((s) => <option key={s} value={s}>{sevKo(s)}</option>)}
            </select>
            <select value={f.author} onChange={set('author')}>
              <option value="ALL">담당자 전체</option>
              {authors.map((a) => <option key={a}>{a}</option>)}
            </select>
            <Link className="btn wh sm ml-auto" to="/app/paste">리뷰 스튜디오 열기</Link>
          </div>

          {!prs ? (
            <div className="panel"><p className="note">불러오는 중…</p></div>
          ) : shown.length === 0 ? (
            <div className="empty">
              <img src={SPR.happy} alt="" />
              <p>{prs.length === 0 ? '아직 이 레포에서 리뷰한 PR이 없어요. 리뷰 스튜디오에서 PR을 가져와보세요.' : '조건에 맞는 PR이 없어요. 필터를 풀어보세요.'}</p>
            </div>
          ) : (
            <div className="panel flush">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>PR</th><th>제목</th><th>작성자</th><th>이슈</th><th>심각도</th><th>상태</th><th>생성일</th>
                  </tr>
                </thead>
                <tbody>
                  {shown.map((pr) => (
                    <tr key={pr.id}>
                      <td className="mono">#{pr.githubPrNumber}</td>
                      <td><Link to={`/app/prs/${pr.id}`} style={{ borderBottom: '2px solid var(--coral)' }}>{pr.title}</Link></td>
                      <td style={{ fontSize: 12.5 }}>{pr.authorName ?? '-'}</td>
                      <td className="mono">{pr.issueCount}</td>
                      <td>{pr.topSeverity ? <SevChip sev={pr.topSeverity} /> : <span className="note sm">-</span>}</td>
                      <td><span className={`chip ${pr.status === 'REVIEWED' ? 'low' : pr.status === 'FAILED' ? 'hi' : 'gray'}`}>{PROCESS_STATUS_KO[pr.status] ?? pr.status}</span></td>
                      <td className="mono xs">{pr.createdAt.slice(0, 10)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </main>
  );
}
