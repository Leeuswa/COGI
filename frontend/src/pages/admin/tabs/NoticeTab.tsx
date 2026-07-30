/*
 * 발송은 서버에서 비동기로 처리 → 폼은 "발송 시작"만 알리고, 실제 성공/실패 건수는 아래 이력 테이블에서 확인.
 * 이력 제목을 누르면 보낸 공지 원문(제목+내용)을 읽기전용 팝업으로 보여준다.
 */
import { useState } from 'react';
import Pager, { pageSlice } from '../../../components/Pager';

const PAGE_SIZE = 10; // 공지는 자동 삭제가 없어 계속 쌓인다 — 이력은 페이지로 끊어 본다

export default function NoticeTab({ notice, setNotice, onSend, busy, notices, onRefresh, onDelete }) {
  const [view, setView] = useState(null); // 이력 원문 팝업 대상
  const [page, setPage] = useState(1);
  const [urgent, setUrgent] = useState(false); // 긴급공지 ON이면 이메일까지 발송, OFF면 인앱 알림만
  const shown = pageSlice(notices, page, PAGE_SIZE);
  return (
    <>
      <div className="panel">
        {/* 헤더 우측에 긴급공지 ON/OFF 토글 — 켜면 이메일까지 발송 */}
        <div className="row" style={{ justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
          <h3 style={{ margin: 0 }}>📢 전체 공지 메일</h3>
          <button type="button" role="switch" aria-checked={urgent} onClick={() => setUrgent((v) => !v)}
            title="켜면 활성 회원 전원에게 이메일 + 인앱 알림을 모두 발송합니다"
            style={{ display: 'inline-flex', alignItems: 'center', gap: 8, cursor: 'pointer',
                     border: '2px solid var(--navy)', borderRadius: 999, padding: '5px 12px',
                     background: urgent ? 'var(--coral)' : 'var(--white)', color: urgent ? 'var(--white)' : 'var(--navy)',
                     fontWeight: 800, fontSize: 12, boxShadow: '3px 3px 0 var(--navy)', transition: 'background .15s, color .15s' }}>
            🚨 긴급공지
            <span style={{ position: 'relative', width: 36, height: 20, borderRadius: 999,
                           background: urgent ? 'var(--white)' : '#cbd3e1', border: '2px solid var(--navy)', flexShrink: 0 }}>
              <span style={{ position: 'absolute', top: 1, left: urgent ? 17 : 1, width: 13, height: 13, borderRadius: '50%',
                             background: 'var(--navy)', transition: 'left .15s' }} />
            </span>
            <span style={{ fontSize: 11 }}>{urgent ? 'ON' : 'OFF'}</span>
          </button>
        </div>
        <p className="note sm" style={{ marginBottom: 16 }}>
          일반 발송은 인앱 알림만 보내요.<br />
          <b>긴급공지</b>를 켜면 이메일까지 함께 발송돼요 (백그라운드 진행, 결과는 아래 이력에서 확인).
        </p>
        <div className="form">
          <div>
            <label>제목</label>
            <input type="text" value={notice.subject}
              onChange={(e) => setNotice((n) => ({ ...n, subject: e.target.value }))} />
          </div>
          <div>
            <label>내용</label>
            <textarea value={notice.content}
              onChange={(e) => setNotice((n) => ({ ...n, content: e.target.value }))} />
          </div>
          <button className="btn co" onClick={() => onSend(urgent)} disabled={busy || !notice.subject.trim() || !notice.content.trim()}>
            {busy ? '발송 중…' : '전체 회원에게 발송'}
          </button>
        </div>
      </div>

      <div className="panel flush">
        <div className="row" style={{ justifyContent: 'space-between', alignItems: 'baseline', padding: '10px 12px' }}>
          <b>발송 이력</b>
          <button className="btn wh sm" onClick={onRefresh}>새로고침</button>
        </div>
        <table className="tbl">
          <thead><tr><th style={{ width: 1, whiteSpace: 'nowrap' }}>발송 시각</th><th>제목</th><th>대상</th><th>상태</th><th>성공/실패</th><th>관리</th></tr></thead>
          <tbody>
            {notices.length === 0 ? (
              <tr><td colSpan={6} className="note sm" style={{ textAlign: 'center' }}>아직 보낸 공지가 없어요.</td></tr>
            ) : shown.map((n) => (
              <tr key={n.id}>
                <td className="mono xs" style={{ whiteSpace: 'nowrap' }}>
                  {n.createdAt.slice(0, 16).replace('T', ' ')}
                  {n.urgent && <span className="chip" style={{ background: 'var(--coral)', color: 'var(--white)', fontSize: 10, padding: '2px 5px', marginLeft: 8, verticalAlign: 'middle' }}>🚨 긴급</span>}
                </td>
                <td style={{ fontSize: 13 }}>
                  <button type="button" className="term-link" onClick={() => setView(n)}>{n.subject}</button>
                </td>
                <td className="mono">{n.recipientCount}</td>
                <td><span className={`chip ${n.status === 'SENT' ? 'low' : 'navy'}`}>{n.status === 'SENT' ? '완료' : '발송 중'}</span></td>
                <td className="mono">{n.status === 'SENT' ? `${n.successCount} / ${n.failCount}` : '—'}</td>
                <td><button className="btn wh sm" onClick={() => onDelete(n)}>삭제</button></td>
              </tr>
            ))}
          </tbody>
        </table>
        <Pager page={page} total={notices.length} size={PAGE_SIZE} onChange={setPage} />
      </div>

      {/* 이력 원문 읽기전용 팝업 (알림처럼 제목+내용만, 수정 불가) */}
      {view && (
        <div className="modal-mask" onClick={() => setView(null)}>
          <div className="modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <h3 style={{ marginTop: 0 }}>📢 {view.subject}</h3>
            <p className="note sm" style={{ marginTop: -4 }}>
              {view.createdAt.slice(0, 16).replace('T', ' ')} · 대상 {view.recipientCount}명
            </p>
            <p style={{ fontSize: 13.5, lineHeight: 1.8, margin: '14px 0 22px', whiteSpace: 'pre-wrap' }}>{view.content}</p>
            <button className="btn wh sm" onClick={() => setView(null)}>닫기</button>
          </div>
        </div>
      )}
    </>
  );
}
