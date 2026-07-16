/*
 * [관리자 탭] 활동 로그 + 전체 공지 메일 (ADM-003, API-019~020, FR-22~23)
 * 공지 발송 결과는 성공/실패 건수로 토스트에 찍힌다 (발송 로그 남김이 인수조건).
 */
export default function LogsTab({ logs, notice, setNotice, onSend, busy }) {
  return (
    <>
      <div className="panel flush">
        <table className="tbl">
          <thead><tr><th>일시</th><th>유저</th><th>액션</th><th>내용</th></tr></thead>
          <tbody>
            {logs.map((l) => (
              <tr key={l.id}>
                <td className="mono xs">{l.createdAt.slice(0, 16).replace('T', ' ')}</td>
                <td style={{ fontSize: 12.5 }}>{l.userEmail}</td>
                <td><span className="chip navy">{l.action}</span></td>
                <td style={{ fontSize: 12 }}>{l.detail}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="panel">
        <h3>📢 전체 공지 메일</h3>
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
          <button className="btn co" onClick={onSend} disabled={busy}>
            {busy ? '발송 중…' : '전체 회원에게 발송'}
          </button>
        </div>
      </div>
    </>
  );
}
