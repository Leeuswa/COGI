/*
 * PR 리뷰 현황 (RDB-002, API-032, 필터 FR-41~42)
 * 원래 명세는 "팀 PR 목록"이었지만 백엔드에 팀 개념이 따로 없어(레포+팀원이 곧 팀) 레포 기준으로 축소.
 * 여러 레포를 연동했다면 먼저 레포를 고른다. 심각도/담당자 필터는 서버에 쿼리 파라미터로 넘겨서 거른다
 * (담당자 드롭다운 옵션은 필터링 전 전체 목록에서 뽑아야 해서 별도로 한 번 더 불러온다).
 */
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/client';
import { PageHead, SevChip } from '../../components/ui';
import { SEVERITIES, sevKo, PROCESS_STATUS_KO } from '../../data/constants';
import { SPR } from '../../assets/sprites';
import useIsMobile from '../../hooks/useIsMobile';
import MobilePrList from '../mobile/MobilePrList';

// 폰이면 데스크톱 본체를 아예 mount하지 않는다.
// 한 컴포넌트 안에서 갈랐더니 조건부 return 위의 useEffect가 그대로 돌아 같은 API를 두 번 때렸다.
// getWeaknessStats처럼 조회할 때마다 통계를 지우고 다시 넣는 API는 두 번 겹치면 한쪽이 빈 목록을 받는다.
export default function PrList() {
  return useIsMobile() ? <MobilePrList /> : <DesktopPrList />;
}

function DesktopPrList() {
  const [repos, setRepos] = useState(null);
  const [repoId, setRepoId] = useState(null);
  const [prs, setPrs] = useState(null); // 서버가 이미 걸러서 내려준, 화면에 실제로 보여줄 목록
  const [authors, setAuthors] = useState<string[]>([]); // 담당자 드롭다운 옵션(FR-42) — 필터 전 전체 목록에서 뽑음
  const [hasAnyPr, setHasAnyPr] = useState(true); // 이 레포에 리뷰한 PR이 하나라도 있는지 — 필터링 전 전체 개수 기준
  const [f, setF] = useState({ severity: 'ALL', author: 'ALL' });

  useEffect(() => {
    api.getMyLinkedRepos().then((list) => {
      setRepos(list);
      if (list.length > 0) setRepoId(list[0].repoId); // 기본: 첫 레포
    });
  }, []);

  // 레포가 바뀔 때만: 필터 드롭다운에 채울 담당자 목록을 전체 목록 기준으로 한 번 뽑아둔다
  useEffect(() => {
    if (repoId == null) return;
    // 레포를 바꾸면 이전 레포 기준 필터는 무의미하므로 초기화 — 이미 기본값이면 새 객체를 만들지 않아
    // 아래 필터링 useEffect가 괜히 한 번 더 도는 것을 막는다
    setF((p) => (p.severity === 'ALL' && p.author === 'ALL' ? p : { severity: 'ALL', author: 'ALL' }));
    api.getRepoReviewedPrs(repoId).then((list) => {
      setAuthors([...new Set<string>(list.map((p) => p.authorName).filter(Boolean))]);
      setHasAnyPr(list.length > 0);
    });
  }, [repoId]);

  // 레포·필터가 바뀔 때마다 서버에 걸러달라고 요청 — 여기서는 받은 걸 그대로 보여주기만 한다
  useEffect(() => {
    if (repoId == null) return;
    setPrs(null);
    api.getRepoReviewedPrs(repoId, f).then(setPrs);
  }, [repoId, f]);

  const set = (k) => (e) => setF((p) => ({ ...p, [k]: e.target.value }));
  const shown = prs || [];


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
              <p>{!hasAnyPr ? '아직 이 레포에서 리뷰한 PR이 없어요. 리뷰 스튜디오에서 PR을 가져와보세요.' : '조건에 맞는 PR이 없어요. 필터를 풀어보세요.'}</p>
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
                      <td style={{ maxWidth: 280, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        <Link to={`/app/prs/${pr.id}`} title={pr.title}>{pr.title}</Link>
                      </td>
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
