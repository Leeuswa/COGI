/*
 * [마이페이지 탭] 약관 동의 현황 (AUTH-009, API-063, FR-89~91)
 * 버전 단위 관리 — 재동의가 필요해지면 앱 상단 배너(App.jsx)가 이 탭으로 안내한다.
 */
import { useState } from 'react';
import { TermsDialog } from '../../../components/ui';

export default function TermsTab({ terms, agreedIds = [] }) {
  const [viewTerm, setViewTerm] = useState(null);
  const agreed = new Set(agreedIds); // 실제 동의한 약관 id
  return (
    <div className="panel flush">
      <table className="tbl">
        <thead><tr><th>약관</th><th>버전</th><th>구분</th><th>동의</th></tr></thead>
        <tbody>
          {terms.map((t) => (
            <tr key={t.id}>
              <td>
                <button type="button" className="term-link" onClick={() => setViewTerm(t)}>{t.title}</button>
              </td>
              <td className="mono">v{t.version}</td>
              <td><span className={`chip ${t.isRequired ? 'navy' : 'gray'}`}>{t.isRequired ? '필수' : '선택'}</span></td>
              <td>{agreed.has(t.id)
                ? <span className="chip low">동의함</span>
                : t.type === 'PAYMENT'
                  ? <span className="chip gray">결제 시 동의</span>
                  : <span className="chip gray">미동의</span>}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <TermsDialog term={viewTerm} onClose={() => setViewTerm(null)} />
    </div>
  );
}
