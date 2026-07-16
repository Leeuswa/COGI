/*
 * 팀 PR 목록 (RDB-002, API-032, 필터 FR-41~42)
 * 기간 / 심각도 / 카테고리 필터. 필터 값은 API 쿼리 파라미터로 그대로 넘어간다.
 * 목 데이터가 1건뿐이라 필터에 안 걸리면 빈 상태가 보이는데, 그게 정상 동작.
 */
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/client';
import { PageHead, SevChip } from '../../components/ui';
import { CATEGORIES, SEVERITIES, catKo, sevKo, PR_STATUS_KO } from '../../data/constants';
import { SPR } from '../../assets/sprites';

export default function PrList() {
  const [prs, setPrs] = useState(null);
  const [f, setF] = useState({ period: 'ALL', severity: 'ALL', category: 'ALL', author: 'ALL' });

  useEffect(() => {
    // teamId 1 고정 — 목 모드는 팀이 하나. 실서버에선 유저의 팀 컨텍스트에서 온다
    api.getTeamPrs(1, f).then(setPrs);
  }, [f]);

  const set = (k) => (e) => setF((p) => ({ ...p, [k]: e.target.value }));

  // 목 모드용 클라이언트 필터. 실서버에선 서버가 걸러서 주므로 그대로 통과된다
  const shown = (prs || []).filter((pr) => {
    if (f.severity !== 'ALL' && pr.topSeverity !== f.severity) return false;
    if (f.author !== 'ALL' && pr.authorName !== f.author) return false;
    return true;
  });
  const authors: string[] = [...new Set<string>((prs || []).map((p) => p.authorName))]; // 담당자 필터 옵션 (FR-42)

  return (
    <main className="app-main">
      <PageHead badge="PULL REQUESTS" title="팀 PR 리뷰 현황" lead="필터로 좁혀서 급한 것부터 처리하세요." />

      <div className="filter-bar">
        <select value={f.period} onChange={set('period')}>
          <option value="ALL">전체 기간</option>
          <option value="7D">최근 7일</option>
          <option value="30D">최근 30일</option>
        </select>
        <select value={f.severity} onChange={set('severity')}>
          <option value="ALL">심각도 전체</option>
          {SEVERITIES.map((s) => <option key={s} value={s}>{sevKo(s)}</option>)}
        </select>
        <select value={f.category} onChange={set('category')}>
          <option value="ALL">카테고리 전체</option>
          {CATEGORIES.map((c) => <option key={c} value={c}>{catKo(c)}</option>)}
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
          <p>조건에 맞는 PR이 없어요. 필터를 풀거나, 레포를 연동해보세요.</p>
          <Link className="btn wh sm" style={{ marginTop: 12, display: 'inline-block' }} to="/app/repos">레포 연동 →</Link>
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
                  <td style={{ fontSize: 12.5 }}>{pr.authorName}</td>
                  <td className="mono">{pr.issueCount}</td>
                  <td><SevChip sev={pr.topSeverity} /></td>
                  <td><span className={`chip ${pr.status === 'REVIEWED' ? 'low' : pr.status === 'FAILED' ? 'hi' : 'gray'}`}>{PR_STATUS_KO[pr.status] ?? pr.status}</span></td>
                  <td className="mono xs">{pr.createdAt.slice(0, 10)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  );
}
