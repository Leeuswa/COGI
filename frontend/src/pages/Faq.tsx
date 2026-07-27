/*
 * FAQ 페이지 (상단 헤더 진입) — 관리자 콘솔에서 등록한 공개 FAQ를 아코디언으로 조회.
 * 질문 클릭 → 답변 펼침. 공개 데이터라 페이지에서 직접 조회한다.
 */
import { useEffect, useState } from 'react';
import * as api from '../api/client';
import { PageHead } from '../components/ui';

export default function Faq() {
  const [faqs, setFaqs] = useState<any[] | null>(null);
  const [open, setOpen] = useState<number | null>(null);

  useEffect(() => { api.getFaqs().then(setFaqs).catch(() => setFaqs([])); }, []);

  return (
    <main className="app-main">
      <PageHead badge="FAQ" title="자주 묻는 질문"
        lead={"궁금한 점을 모아뒀어요. 질문을 누르면 답변이 펼쳐져요."} />

      {!faqs ? (
        <div className="panel"><p className="note">불러오는 중…</p></div>
      ) : faqs.length === 0 ? (
        <div className="empty"><p>아직 등록된 FAQ가 없어요.</p></div>
      ) : (
        <div className="faq-list">
          {faqs.map((f) => {
            const isOpen = open === f.id;
            return (
              <div key={f.id} className={`faq-item${isOpen ? ' open' : ''}`}>
                <button type="button" className="faq-q" onClick={() => setOpen(isOpen ? null : f.id)}>
                  {f.category && <span className="chip navy sm">{f.category}</span>}
                  <span className="faq-q-text">{f.question}</span>
                  <span className="faq-caret">{isOpen ? '−' : '+'}</span>
                </button>
                {isOpen && <div className="faq-a">{f.answer}</div>}
              </div>
            );
          })}
        </div>
      )}
    </main>
  );
}
