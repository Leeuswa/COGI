/*
 * [마이페이지 탭] 공지사항 — 관리자가 전체 발송한 공지를 조회 전용으로 본다.
 * 제목 클릭 → 내용 펼침(아코디언). FAQ 탭과 동일한 스타일 재사용.
 */
import { useEffect, useState } from 'react';
import * as api from '../../../api/client';

const fmt = (d) => (d ? d.slice(0, 10).replace(/-/g, '.') : ''); // '2026-07-27T…' → '2026.07.27'

export default function NoticeTab() {
  const [notices, setNotices] = useState<any[] | null>(null);
  const [open, setOpen] = useState<number | null>(null);

  useEffect(() => { api.getNotices().then(setNotices).catch(() => setNotices([])); }, []);

  if (!notices) return <div className="panel"><p className="note">불러오는 중…</p></div>;
  if (notices.length === 0) return <div className="empty"><p>아직 공지가 없어요.</p></div>;

  return (
    <div className="faq-list">
      {notices.map((n) => {
        const isOpen = open === n.id;
        return (
          <div key={n.id} className={`faq-item${isOpen ? ' open' : ''}`}>
            <button type="button" className="faq-q" onClick={() => setOpen(isOpen ? null : n.id)}>
              <span className="faq-q-text">{n.subject}</span>
              <span className="mono xs" style={{ color: 'var(--sub)' }}>{fmt(n.sentAt)}</span>
              <span className="faq-caret">{isOpen ? '−' : '+'}</span>
            </button>
            {isOpen && <div className="faq-a">{n.content}</div>}
          </div>
        );
      })}
    </div>
  );
}
