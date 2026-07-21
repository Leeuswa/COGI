/*
 * 자잘한 공용 컴포넌트 모음. 파일 하나로 충분해서 안 쪼갬.
 */
import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth, SESSION_MS } from '../context/AuthContext';
import { useGame } from '../context/GameContext';

/* ── 세션(JWT) 카운트다운 + 연장 — 0이 되면 자동 로그아웃 ── */
function SessionTimer() {
  const { signOut } = useAuth();
  const nav = useNavigate();
  // 만료 예정 시각(ms). 없으면(기존 세션) 지금+1시간으로 백필해 즉시 로그아웃되는 걸 막는다.
  const expiresAt = () => {
    let v = Number(localStorage.getItem('cogi-expires') || 0);
    if (!v) { v = Date.now() + SESSION_MS; localStorage.setItem('cogi-expires', String(v)); }
    return v;
  };
  const [left, setLeft] = useState(() => expiresAt() - Date.now());
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const t = setInterval(() => {
      const r = expiresAt() - Date.now();
      setLeft(r);
      if (r <= 0) {                       // 만료 → 자동 로그아웃
        clearInterval(t);
        localStorage.removeItem('cogi-expires');
        signOut();
        nav('/login?expired=1', { replace: true });
      }
    }, 1000);
    return () => clearInterval(t);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const extend = async () => {            // 세션 연장 — 새 토큰 재발급받고 타이머 리셋
    setBusy(true);
    try {
      await api.extendSession();
      localStorage.setItem('cogi-expires', String(Date.now() + SESSION_MS));
      setLeft(SESSION_MS);
    } catch { /* 백엔드 refresh 미구현/만료 시 무시 */ }
    finally { setBusy(false); }
  };

  const s = Math.max(0, Math.floor(left / 1000));
  const hms = `${Math.floor(s / 3600)}:${String(Math.floor((s % 3600) / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`; // 시:분:초
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontFamily: 'Silkscreen, monospace', fontSize: 12, whiteSpace: 'nowrap', flexShrink: 0 }}>
      ⏱ {hms}
      <button className="btn sm wh" onClick={extend} disabled={busy}>{busy ? '연장 중…' : '연장'}</button>
    </span>
  );
}

/* ── 상단 GNB. 로그인 전/후 메뉴가 달라진다 ── */
export function Nav() {
  const { user, signOut } = useAuth();
  const { S, creditLimit } = useGame();
  const nav = useNavigate();

  const logout = () => { signOut(); nav('/'); };

  return (
    <nav className="gnb">
      <Link className="logo" to={user ? '/app' : '/'}>
        <img src="/logo.png" alt="COGI Code Guide" />
      </Link>

      {user ? (
        <div className="links">
          <NavLink to="/app" end className={({ isActive }) => (isActive ? 'on' : '')}>대시보드</NavLink>
          <NavLink to="/app/prs" className={({ isActive }) => (isActive ? 'on' : '')}>PR 리뷰</NavLink>
          <NavLink to="/app/paste" className={({ isActive }) => (isActive ? 'on' : '')}>리뷰 스튜디오</NavLink>
          <NavLink to="/app/history" className={({ isActive }) => (isActive ? 'on' : '')}>리뷰 히스토리</NavLink>
          <NavLink to="/app/weakness" className={({ isActive }) => (isActive ? 'on' : '')}>약점</NavLink>
          <NavLink to="/app/cards" className={({ isActive }) => (isActive ? 'on' : '')}>학습카드</NavLink>
          <NavLink to="/app/courses" className={({ isActive }) => (isActive ? 'on' : '')}>강의</NavLink>
          <NavLink to="/app/skills" className={({ isActive }) => (isActive ? 'on' : '')}>스킬 추천</NavLink>
          <NavLink to="/app/growth" className={({ isActive }) => (isActive ? 'on' : '')}>성장</NavLink>
          <NavLink to="/app/reports" className={({ isActive }) => (isActive ? 'on' : '')}>주간리포트</NavLink>
          <NavLink to="/app/team" className={({ isActive }) => (isActive ? 'on' : '')}>팀</NavLink>
          <NavLink to="/app/plan" className={({ isActive }) => (isActive ? 'on' : '')}>요금제</NavLink>
          <NavLink to="/app/my" className={({ isActive }) => (isActive ? 'on' : '')}>마이</NavLink>
          {user.role === 'ADMIN' && (
            <NavLink to="/app/admin" className={({ isActive }) => (isActive ? 'on' : '')}>관리자</NavLink>
          )}
          <Bell loginId={user.loginId} />
          {/* 코인/크레딧 요약. 눌러도 아무 일 없음, 상태 표시용 */}
          <span style={{ fontFamily: 'Silkscreen, monospace', fontSize: 12 }}>
            🪙{S.coins} · ⚡{creditLimit - S.creditUsed}
          </span>
          {/* GitHub 미연동 계정에만 노출 — 연동된 계정은 팀 페이지의 '레포 연동 관리'로 통한다 */}
          {!user.githubUsername && <Link className="btn sm wh" to="/app/repos">🐙 GitHub 연동</Link>}
          <SessionTimer />
          <button className="btn sm wh" onClick={logout}>로그아웃</button>
        </div>
      ) : (
        <div className="links">
          {/* 실제 앱 페이지로 연결 — 미로그인이면 RequireAuth가 로그인으로 보냈다가 그 페이지로 복귀시킨다 */}
          <Link to="/app/prs">코드리뷰</Link>
          <Link to="/app/weakness">학습</Link>
          <Link to="/app/growth">성장</Link>
          <Link to="/app/plan">요금제</Link>
          <Link to="/guest">게스트 체험</Link>
          <Link to="/login">로그인</Link>
          <Link className="btn co sm" to="/signup">회원가입</Link>
        </div>
      )}
    </nav>
  );
}

/* ── 푸터 ── */
export function Footer() {
  return (
    <footer className="ft">
      <div>Team Fable · 상연 · 이수환 · 홍성찬 · 유지환</div>
      <div>KDT 부트캠프 8회차 해커톤 · COGI · Code Guide</div>
    </footer>
  );
}

/* ── 스크롤 등장 (.rv → .show). 원본 IntersectionObserver 로직 그대로 ── */
export function Reveal({ children, className = '', delay = 0, as: Tag = 'div' as any, ...rest }) {
  const ref = useRef(null);
  useEffect(() => {
    const el = ref.current;
    const io = new IntersectionObserver(
      (es) => es.forEach((e) => { if (e.isIntersecting) { e.target.classList.add('show'); io.unobserve(e.target); } }),
      { threshold: 0.15 }
    );
    el.style.transitionDelay = delay + 'ms';
    io.observe(el);
    return () => io.disconnect();
  }, [delay]);
  return <Tag ref={ref} className={`rv ${className}`} {...rest}>{children}</Tag>;
}

/* ── 심각도 칩 (CRITICAL/MAJOR/MINOR → 색 매핑) ── */
import { sevKo } from '../data/constants';
import * as api from '../api/client';

/* 알림 종 — 초대 등 이벤트가 여기로 온다. x 로 지우고, 누르면 해당 화면으로 */
function Bell({ loginId }) {
  const [list, setList] = useState([]);
  const [open, setOpen] = useState(false);
  const boxRef = useRef(null);
  const nav = useNavigate();

  useEffect(() => { api.getNotifications(loginId).then(setList); }, [loginId]);
  useEffect(() => { // 바깥 클릭으로 닫기
    const close = (e) => { if (boxRef.current && !boxRef.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const dismiss = (e, n) => {
    e.stopPropagation(); // 항목 클릭(이동)과 분리
    api.dismissNotification(n.id);
    setList((l) => l.filter((x) => x.id !== n.id));
  };
  const go = (n) => { setOpen(false); nav(n.link); };

  return (
    <span className="bell-wrap" ref={boxRef}>
      <button className="bell" title="알림" onClick={() => setOpen((o) => !o)}>
        🔔{list.length > 0 && <i className="bell-badge">{list.length}</i>}
      </button>
      {open && (
        <div className="bell-drop">
          {list.length === 0 ? (
            <p className="note sm" style={{ padding: 14 }}>새 알림이 없어요.</p>
          ) : (
            list.map((n) => (
              <div key={n.id} className="noti" onClick={() => go(n)}>
                <span className="noti-icon">{n.icon}</span>
                <div className="noti-body">
                  {n.title && <b className="noti-title">{n.title}</b>}
                  <p>{n.text}</p>
                </div>
                <button className="noti-x" title="알림 삭제" onClick={(e) => dismiss(e, n)}>✕</button>
              </div>
            ))
          )}
        </div>
      )}
    </span>
  );
}

export function SevChip({ sev }) {
  const cls = sev === 'CRITICAL' ? 'hi' : sev === 'MAJOR' ? 'mid' : 'low';
  return <span className={`chip ${cls}`}>{sevKo(sev)}</span>; // 코드값은 유지, 표기만 한글
}

// AI가 이슈 설명에 섞어 보내는 마크다운 코드 표기(```블록, `인라인`)만 구분해서 렌더링.
// 마크다운 라이브러리 없이, 정규식으로 코드 부분만 잘라내 코드체로 보여준다.
// 스튜디오 채팅 말풍선/리뷰 히스토리 상세 팝업 등 이슈 설명을 보여주는 곳이면 공용으로 쓴다.
export function renderDescription(text: string) {
  const parts: React.ReactNode[] = [];
  const regex = /```[\w-]*\n?([\s\S]*?)```|`([^`\n]+)`/g;
  let lastIndex = 0;
  let match;
  let key = 0;
  while ((match = regex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push(<span key={key++}>{text.slice(lastIndex, match.index)}</span>);
    }
    if (match[1] !== undefined) {
      parts.push(
        <pre key={key++} className="codebox snippet">
          {match[1].trim()}
        </pre>,
      );
    } else {
      parts.push(
        <code key={key++} className="inline-code">
          {match[2]}
        </code>,
      );
    }
    lastIndex = regex.lastIndex;
  }
  if (lastIndex < text.length) {
    parts.push(<span key={key++}>{text.slice(lastIndex)}</span>);
  }
  return parts;
}

/* ── 신호등 등급. 심각도 색과 헷갈리지 않게 라벨을 반드시 같이 표기 (FR-61 비고) ── */
export function GradeLight({ grade }) {
  const map = { RED: ['red', '학습 시작'], YELLOW: ['yellow', '학습 진행중'], GREEN: ['green', '해결'], GREEN_PLUS: ['green', '해결+'] };
  const [cls, label] = map[grade] ?? map.RED;
  return <span className={`light ${cls}`}><i />{label}</span>;
}

/* ── 페이지 머리말 ── */
export function PageHead({ badge, title, lead, badgeCls = '' }) {
  return (
    <div className="page-head">
      <span className={`badge ${badgeCls}`}>{badge}</span>
      <h2 className="sec-title">{title}</h2>
      {lead && <p className="sec-lead" style={{ marginBottom: 0 }}>{lead}</p>}
    </div>
  );
}

/* ── 탭 줄 (관리자/마이페이지 공용) — items: [key, label][] ── */
export function Tabs({ items, value, onChange }) {
  return (
    <div className="tabs" role="tablist">
      {items.map(([k, label]) => (
        <button key={k} role="tab" aria-selected={value === k}
          className={value === k ? 'on' : ''} onClick={() => onChange(k)}>{label}</button>
      ))}
    </div>
  );
}

/* ── 약관 팝업: 제목 링크를 누르면 본문을 모달로 (가입·마이페이지 공용) ── */
export function TermsDialog({ term, onClose }) {
  if (!term) return null;
  return (
    <div className="modal-mask" onClick={onClose}>
      <div className="modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
        <h3>{term.title} <span className="chip navy">v{term.version}</span></h3>
        <p style={{ fontSize: 13.5, lineHeight: 2, margin: '14px 0 22px' }}>{term.content}</p>
        <button className="btn wh sm" onClick={onClose}>닫기</button>
      </div>
    </div>
  );
}
