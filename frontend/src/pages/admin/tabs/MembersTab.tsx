/*
 * [관리자 탭] 회원 관리 (ADM-002, API-016~018, FR-20~21)
 * 상태(ACTIVE/SUSPENDED)·권한(USER/ADMIN) 토글 — 저장 즉시 반영이 요구사항이라 확인창 없이 바로 바꾼다.
 */
import { useState } from 'react';
import Pager, { pageSlice } from '../../../components/Pager';

const PAGE_SIZE = 15; // 회원이 늘어도 표는 페이지로 끊어 본다

// 정렬 우선순위: 본인(-1) 최상단 → 일반(0) → 탈퇴(1) 최하단
const rankOf = (m, me) => (m.status === 'WITHDRAWN' ? 1 : m.email === me ? -1 : 0);

export default function MembersTab({ members, onStatus, onRole, onDelete, me }) {
  const [page, setPage] = useState(1); // 목록이 줄면 Pager가 마지막 페이지로 접어준다

  // 본인 행은 최상단, 탈퇴 회원은 최하단 (원본 훼손 방지 위해 복사본 정렬) → 정렬 후 페이지 단위로 자른다
  const sorted = [...members].sort((a, b) => rankOf(a, me) - rankOf(b, me));

  return (
    <div className="panel flush">
      <table className="tbl">
        <thead><tr><th>이메일</th><th>플랜</th><th>권한</th><th>상태</th><th>가입일</th><th>액션</th></tr></thead>
        <tbody>
          {pageSlice(sorted, page, PAGE_SIZE).map((m) => {
            const isMe = !!m.email && m.email === me; // 본인 행 — 정지/권한회수 불가(서버도 403으로 막음)
            const gone = m.status === 'WITHDRAWN';     // 탈퇴 회원 — 익명화 이메일 대신 표기, 액션 전부 차단
            return (
            <tr key={m.id}>
              <td>
                {gone ? <span className="note">탈퇴한 회원</span> : m.email}
                {isMe && <span className="chip hi" style={{ marginLeft: 6 }}>본인</span>}
              </td>
              <td className="mono">{m.planName}</td>
              <td><span className={`chip ${m.role === 'ADMIN' ? 'hi' : 'gray'}`}>{m.role}</span></td>
              <td><span className={`chip ${m.status === 'ACTIVE' ? 'low' : 'gray'}`}>{m.status}</span></td>
              <td className="mono xs">{m.createdAt?.slice(0, 10)}</td>
              <td style={{ display: 'flex', gap: 6 }}>
                <button className="btn wh sm" disabled={isMe || gone} onClick={() => onStatus(m)}>{m.status === 'ACTIVE' ? '정지' : '해제'}</button>
                <button className="btn wh sm" disabled={isMe || gone} onClick={() => onRole(m)}>{m.role === 'ADMIN' ? '권한 회수' : '관리자로'}</button>
                {/* 탈퇴 회원만 완전 삭제 가능 — 그 외에는 비활성 */}
                <button className="btn wh sm" disabled={!gone} onClick={() => onDelete(m)}>삭제</button>
              </td>
            </tr>
            );
          })}
        </tbody>
      </table>
      <Pager page={page} total={members.length} size={PAGE_SIZE} onChange={setPage} />
    </div>
  );
}
