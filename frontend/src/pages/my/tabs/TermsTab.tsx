/*
 * [마이페이지 탭] 약관 동의 현황 (AUTH-009, API-063, FR-89~91)
 * 버전 단위 관리 — 재동의가 필요해지면 앱 상단 배너(App.jsx)가 이 탭으로 안내한다.
 */
import { useState } from 'react';
import * as api from '../../../api/client';
import { TermsDialog } from '../../../components/ui';

export default function TermsTab({ terms, agreedIds = [], agreedVers = [], onReagreed }) {
  const [viewTerm, setViewTerm] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const agreed = new Set(agreedIds); // 실제 동의한 약관 id
  // termId -> 동의 당시 버전 (개정 여부 판정)
  const verOf = new Map(agreedVers.map((a) => [a.termId, a.agreedVersion]));
  // 동의했지만 동의 당시 버전이 현재 버전과 다르면 "개정됨"
  const isRevised = (t) => agreed.has(t.id) && verOf.get(t.id) !== t.version;

  // 동의 상태 전송 (agreed=true 동의/재동의, false 철회). 선택 약관 토글·재동의 공용.
  const setAgree = async (t, agree) => {
    setBusyId(t.id);
    try {
      await api.submitAgreements([{ termId: t.id, agreed: agree }]);
      onReagreed?.();
    } finally { setBusyId(null); }
  };

  return (
    <div className="panel flush">
      <table className="tbl">
        <thead><tr><th>약관</th><th>버전</th><th>구분</th><th>동의</th></tr></thead>
        <tbody>
          {terms.map((t) => {
            const on = agreed.has(t.id);
            const revised = isRevised(t);
            const busy = busyId === t.id;
            return (
              <tr key={t.id}>
                <td>
                  <button type="button" className="term-link" onClick={() => setViewTerm(t)}>{t.title}</button>
                </td>
                {/* 버전 + 개정 표시(업데이트됨 칩 + 재동의) */}
                <td className="mono">
                  v{t.version}
                  {revised && (
                    <>
                      {' '}<span className="chip hi sm">업데이트됨</span>
                      {!t.isRequired && (
                        <> <button type="button" className="btn wh sm term-btn" disabled={busy} onClick={() => setAgree(t, true)}>
                          {busy ? '…' : '재동의'}
                        </button></>
                      )}
                    </>
                  )}
                </td>
                <td><span className={`chip ${t.isRequired ? 'navy' : 'gray'}`}>{t.isRequired ? '필수' : '선택'}</span></td>
                <td>
                  {t.isRequired ? (
                    <span className="chip low">동의함</span>
                  ) : t.type === 'PAYMENT' ? (
                    <span className="chip gray">결제 시 동의</span>
                  ) : (
                    // 선택 — 동의/해제 토글 버튼
                    <button type="button" className={`btn sm term-btn ${on ? 'wh' : 'co'}`} disabled={busy}
                      onClick={() => setAgree(t, !on)}>
                      {busy ? '…' : on ? '동의 해제' : '동의'}
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <TermsDialog term={viewTerm} onClose={() => setViewTerm(null)} />
    </div>
  );
}
