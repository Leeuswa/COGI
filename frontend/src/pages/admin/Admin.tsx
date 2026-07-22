/*
 * 관리자 컨테이너 (ADM-001~004, API-014~021)
 * role=ADMIN 만 접근. 프론트에서도 막지만 실서버는 @PreAuthorize 가 403으로 이중 방어.
 * 데이터 조회와 변경 핸들러는 여기서 갖고, 화면은 tabs/ 아래 파일이 하나씩 담당한다.
 * (조회를 컨테이너에 모은 이유: 탭을 오갈 때마다 재조회하지 않으려고)
 */
import { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import * as api from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { useGame } from '../../context/GameContext';
import { PageHead, Tabs } from '../../components/ui';
import UsageTab from './tabs/UsageTab';
import MembersTab from './tabs/MembersTab';
import NoticeTab from './tabs/NoticeTab';
import GuidesTab from './tabs/GuidesTab';
import TermsTab from './tabs/TermsTab';

export default function Admin() {
  const { user } = useAuth();
  const { notify } = useGame();

  const [tab, setTab] = useState('usage');
  const [usage, setUsage] = useState([]);
  const [members, setMembers] = useState([]);
  const [guides, setGuides] = useState(null);
  const [terms, setTerms] = useState([]);
  const [notice, setNotice] = useState({ subject: '', content: '' });
  const [notices, setNotices] = useState([]);
  // 기본 조회 기간: 오늘 기준 최근 한 달 (고정 날짜 아님 — 매 접속 시 최신화)
  const [range, setRange] = useState(() => {
    const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    const today = new Date();
    const monthAgo = new Date();
    monthAgo.setMonth(monthAgo.getMonth() - 1);
    return { from: fmt(monthAgo), to: fmt(today) };
  });
  const [latency, setLatency] = useState(null); // 리뷰 응답시간 (API-054)
  const [busy, setBusy] = useState(false);

  const isAdmin = user.role === 'ADMIN';

  useEffect(() => {
    if (!isAdmin) return;
    api.adminAiUsage(range.from, range.to).then(setUsage);
    api.getReviewLatency(range.from, range.to).then(setLatency);
    api.adminMembers().then(setMembers);
    api.adminGetGuidelines().then(setGuides);
    api.adminGetTerms().then(setTerms);
    api.adminNotices().then(setNotices);
  }, [isAdmin, range]);

  // 일반 유저가 URL로 직접 들어온 경우
  if (!isAdmin) return <Navigate to="/app" replace />;

  // 상태/권한은 저장 즉시 반영이 요구사항 (FR-21)
  // 본인 정지/권한회수는 서버가 403으로 막는다 — 실패 메시지를 그대로 보여준다(락아웃 방지 안내)
  const changeStatus = async (m) => {
    const next = m.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    try {
      await api.adminChangeStatus(m.id, next);
      setMembers((ms) => ms.map((x) => (x.id === m.id ? { ...x, status: next } : x)));
      notify(`${m.email} → ${next} (즉시 반영)`);
    } catch (e) {
      notify(e?.message || '상태 변경에 실패했어요.');
    }
  };

  const changeRole = async (m) => {
    const next = m.role === 'ADMIN' ? 'USER' : 'ADMIN';
    try {
      await api.adminChangeRole(m.id, next);
      setMembers((ms) => ms.map((x) => (x.id === m.id ? { ...x, role: next } : x)));
      notify(`${m.email} 권한 → ${next}`);
    } catch (e) {
      notify(e?.message || '권한 변경에 실패했어요.');
    }
  };

  const sendNotice = async () => {
    if (!notice.subject.trim() || !notice.content.trim() || busy) return;
    setBusy(true);
    try {
      const res = await api.adminSendNotice(notice.subject, notice.content);
      notify(`${res.recipientCount}명에게 공지 발송을 시작했어요 — 결과는 이력에서 확인하세요.`);
      setNotice({ subject: '', content: '' });
      api.adminNotices().then(setNotices); // 방금 보낸 건(발송 중)을 이력에 반영
    } finally { setBusy(false); }
  };

  const saveGuide = async (level) => {
    await api.adminSaveGuideline(level, guides[level]);
    notify(`${level} 지침 저장 — 다음 리뷰부터 반영`);
  };

  const saveTerm = async (t) => {
    try {
      await api.adminUpdateTerm(t.id, t.content);
      setTerms(await api.adminGetTerms()); // 올라간 버전·시행일을 즉시 반영
      notify(`${t.title} 약관 저장됨 — 버전이 올라갔어요`);
    } catch (e) {
      notify(e?.message || '약관 저장에 실패했어요.');
    }
  };

  return (
    <main className="app-main">
      <PageHead badge="ADMIN" badgeCls="co" title="관리자 콘솔" lead={"운영 지표, 회원, AI 리뷰 지침을 한곳에서 관리해요."} />

      <Tabs items={[['usage', 'AI 사용량'], ['members', '회원 관리'], ['notice', '전체 공지'], ['guides', '리뷰 지침'], ['terms', '약관 관리']]} value={tab} onChange={setTab} />

      {tab === 'usage' && <UsageTab usage={usage} range={range} setRange={setRange} latency={latency} />}
      {tab === 'members' && <MembersTab members={members} onStatus={changeStatus} onRole={changeRole} me={user.email} />}
      {tab === 'notice' && <NoticeTab notice={notice} setNotice={setNotice} onSend={sendNotice} busy={busy} notices={notices} onRefresh={() => api.adminNotices().then(setNotices)} />}
      {tab === 'guides' && guides && <GuidesTab guides={guides} setGuides={setGuides} onSave={saveGuide} />}
      {tab === 'terms' && <TermsTab terms={terms} setTerms={setTerms} onSave={saveTerm} />}
    </main>
  );
}
