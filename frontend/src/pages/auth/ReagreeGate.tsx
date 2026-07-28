/*
 * 약관 재동의 게이트 (FR-91)
 * 필수 약관이 개정되면 로그인(소셜 포함) 후 대시보드 진입 전 이 화면이 전체를 덮는다.
 * 온보딩처럼 스킵 불가 — 개정된 필수 약관을 모두 다시 동의해야 통과.
 * 대상 약관 목록/본문은 서버(checkReagreement)가 내려준 terms 를 그대로 쓴다.
 */
import { useState } from 'react';
import * as api from '../../api/client';
import { TermsDialog } from '../../components/ui';

export default function ReagreeGate({ terms, onDone }) {
  const [agreed, setAgreed] = useState([]); // 다시 동의한 약관 id
  const [viewTerm, setViewTerm] = useState(null); // 본문 팝업
  const [busy, setBusy] = useState(false);

  const toggle = (id) =>
    setAgreed((p) => (p.includes(id) ? p.filter((x) => x !== id) : [...p, id]));
  // 재동의 대상은 전부 필수 → 전부 체크해야 진행
  const allOk = terms.every((t) => agreed.includes(t.id));

  const submit = async () => {
    setBusy(true);
    try {
      await api.submitAgreements(terms.map((t) => ({ termId: t.id, agreed: true })));
      onDone();
    } finally { setBusy(false); }
  };

  return (
    <div className="modal-mask">
      <div className="modal" role="dialog" aria-modal="true" style={{ maxWidth: 560 }}>
        <span className="badge co">약관 개정 안내</span>
        <h3 style={{ marginTop: 10 }}>다시 동의가 필요해요</h3>
        <p style={{ fontSize: 13.5, lineHeight: 1.8, color: 'var(--sub)', margin: '8px 0 16px' }}>
          아래 약관이 개정되었습니다. 계속 이용하려면 개정된 내용에 다시 동의해주세요.
        </p>

        {terms.map((t) => (
          <label key={t.id} className="check-row" style={{ marginBottom: 8 }}>
            <input type="checkbox" checked={agreed.includes(t.id)} onChange={() => toggle(t.id)} />
            <span>
              [필수]{' '}
              <button type="button" className="term-link"
                onClick={(e) => { e.preventDefault(); e.stopPropagation(); setViewTerm(t); }}>
                {t.title}
              </button>{' '}
              (v{t.version})
            </span>
          </label>
        ))}

        <button className="btn co mt18" disabled={!allOk || busy} onClick={submit}>
          {busy ? '저장 중…' : '동의하고 계속하기'}
        </button>

        <TermsDialog term={viewTerm} onClose={() => setViewTerm(null)} />
      </div>
    </div>
  );
}
