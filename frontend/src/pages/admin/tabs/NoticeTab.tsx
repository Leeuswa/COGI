/*
 * 발송은 서버에서 비동기로 처리 → 폼은 "발송 시작"만 알리고, 실제 성공/실패 건수는 아래 이력 테이블에서 확인.
 */
export default function NoticeTab({ notice, setNotice, onSend, busy, notices, onRefresh }) {
  return (
    <>
      <div className="panel">
        <h3>📢 전체 공지 메일</h3>
        <p className="note sm">활성 회원 전원에게 메일로 발송돼요. 발송은 백그라운드로 진행되고, 결과는 아래 이력에서 확인할 수 있어요.</p>
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
          <button className="btn co" onClick={onSend} disabled={busy || !notice.subject.trim() || !notice.content.trim()}>
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
          <thead><tr><th>발송 시각</th><th>제목</th><th>대상</th><th>상태</th><th>성공/실패</th></tr></thead>
          <tbody>
            {notices.length === 0 ? (
              <tr><td colSpan={5} className="note sm" style={{ textAlign: 'center' }}>아직 보낸 공지가 없어요.</td></tr>
            ) : notices.map((n) => (
              <tr key={n.id}>
                <td className="mono xs">{n.createdAt.slice(0, 16).replace('T', ' ')}</td>
                <td style={{ fontSize: 13 }}>{n.subject}</td>
                <td className="mono">{n.recipientCount}</td>
                <td><span className={`chip ${n.status === 'SENT' ? 'low' : 'navy'}`}>{n.status === 'SENT' ? '완료' : '발송 중'}</span></td>
                <td className="mono">{n.status === 'SENT' ? `${n.successCount} / ${n.failCount}` : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
