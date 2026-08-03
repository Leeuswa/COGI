/*
 * [관리자 탭] 회원 관리 (ADM-002, API-016~018, FR-20~21)
 * 상태(ACTIVE/SUSPENDED)·권한(USER/ADMIN) 토글 — 저장 즉시 반영이 요구사항이라 확인창 없이 바로 바꾼다.
 * 삭제만 되돌릴 수 없어서 정지된 계정에서만 열리고, 확인창은 컨테이너(Admin.tsx)에서 받는다.
 */
import { useState } from 'react';
import Pager, { pageSlice } from '../../../components/Pager';

const PAGE_SIZE = 15; // 회원은 계속 늘어난다 — 표는 페이지로 끊어 본다

// 소셜 회원도 같은 목록에서 보되 어떤 경로로 가입했는지는 구분해서 보여준다
const PROVIDER_LABEL = { LOCAL: '이메일', GITHUB: 'GitHub', KAKAO: '카카오' };

// 정렬 우선순위: 본인(-1) 최상단 → 일반(0) → 탈퇴(1) 최하단
const rankOf = (m, me) => (m.status === 'WITHDRAWN' ? 1 : m.email === me ? -1 : 0);

export default function MembersTab({ members, onStatus, onRole, onDelete, me }) {
  const [page, setPage] = useState(1); // 삭제로 목록이 줄면 Pager가 마지막 페이지로 접어준다

  // 본인 행은 최상단, 탈퇴 회원은 최하단 (원본 훼손 방지 위해 복사본 정렬)
  const sorted = [...members].sort((a, b) => rankOf(a, me) - rankOf(b, me));

  return (
    <div className="panel flush">
      <table className="tbl">
        <thead><tr><th>회원</th><th>가입방식</th><th>플랜</th><th>권한</th><th>상태</th><th>가입일</th><th>액션</th></tr></thead>
        <tbody>
          {pageSlice(sorted, page, PAGE_SIZE).map((m) => {
            const isMe = !!m.email && m.email === me; // 본인 행 — 정지/권한회수/삭제 불가(서버도 403으로 막음)
            const gone = m.status === 'WITHDRAWN';     // 탈퇴 회원 — 익명화 이메일 대신 표기, 액션 전부 차단
            // 소셜 가입은 이메일 제공 동의를 안 하면 email이 null이라 닉네임을 대신 보여준다
            const label = gone ? '탈퇴한 회원' : (m.email || m.nickname || `#${m.id}`);
            const canDelete = !isMe && m.status === 'SUSPENDED'; // 삭제 가능할 때만 빨간 버튼으로 눈에 띄게
            return (
            <tr key={m.id}>
              <td>
                {gone ? <span className="note">{label}</span> : label}
                {isMe && <span className="chip hi" style={{ marginLeft: 6 }}>본인</span>}
                {/* 이메일로 표기한 소셜 회원은 닉네임을 보조로 붙여 누군지 알아보게 한다 */}
                {!gone && m.email && m.nickname && <div className="note xs">{m.nickname}</div>}
              </td>
              <td><span className="chip gray">{PROVIDER_LABEL[m.provider] || m.provider || '이메일'}</span></td>
              <td className="mono">{m.planName}</td>
              <td><span className={`chip ${m.role === 'ADMIN' ? 'hi' : 'gray'}`}>{m.role}</span></td>
              <td><span className={`chip ${m.status === 'ACTIVE' ? 'low' : 'gray'}`}>{m.status}</span></td>
              <td className="mono xs">{m.createdAt?.slice(0, 10)}</td>
              <td style={{ display: 'flex', gap: 6 }}>
                <button className="btn wh sm" disabled={isMe || gone} onClick={() => onStatus(m)}>{m.status === 'ACTIVE' ? '정지' : '해제'}</button>
                <button className="btn wh sm" disabled={isMe || gone} onClick={() => onRole(m)}>{m.role === 'ADMIN' ? '권한 회수' : '관리자로'}</button>
                {/* 정지된 계정만 삭제 — 서버도 409로 막지만 버튼부터 잠가 오조작을 줄인다 */}
                <button className={`btn ${canDelete ? 'danger' : 'wh'} sm`} disabled={!canDelete}
                  title={m.status !== 'SUSPENDED' ? '정지된 계정만 삭제할 수 있어요' : undefined}
                  onClick={() => onDelete(m)}>삭제</button>
              </td>
            </tr>
            );
          })}
        </tbody>
      </table>
      <Pager page={page} total={sorted.length} size={PAGE_SIZE} onChange={setPage} />
    </div>
  );
}
