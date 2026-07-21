/*
 * [관리자 탭] 회원 관리 (ADM-002, API-016~018, FR-20~21)
 * 상태(ACTIVE/SUSPENDED)·권한(USER/ADMIN) 토글 — 저장 즉시 반영이 요구사항이라 확인창 없이 바로 바꾼다.
 */
export default function MembersTab({ members, onStatus, onRole, me }) {
  return (
    <div className="panel flush">
      <table className="tbl">
        <thead><tr><th>이메일</th><th>플랜</th><th>권한</th><th>상태</th><th>액션</th></tr></thead>
        <tbody>
          {/* 본인 행을 항상 최상단으로 (원본 훼손 방지 위해 복사본 정렬) */}
          {[...members].sort((a, b) => (b.email === me ? 1 : 0) - (a.email === me ? 1 : 0)).map((m) => {
            const isMe = !!m.email && m.email === me; // 본인 행 — 정지/권한회수 불가(서버도 403으로 막음)
            return (
            <tr key={m.id}>
              <td>
                {m.email}
                {isMe && <span className="chip hi" style={{ marginLeft: 6 }}>본인</span>}
              </td>
              <td className="mono">{m.planName}</td>
              <td><span className={`chip ${m.role === 'ADMIN' ? 'hi' : 'gray'}`}>{m.role}</span></td>
              <td><span className={`chip ${m.status === 'ACTIVE' ? 'low' : 'gray'}`}>{m.status}</span></td>
              <td style={{ display: 'flex', gap: 6 }}>
                <button className="btn wh sm" disabled={isMe} onClick={() => onStatus(m)}>{m.status === 'ACTIVE' ? '정지' : '해제'}</button>
                <button className="btn wh sm" disabled={isMe} onClick={() => onRole(m)}>{m.role === 'ADMIN' ? '권한 회수' : '관리자로'}</button>
              </td>
            </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
