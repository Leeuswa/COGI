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
import LogsTab from './tabs/LogsTab';
import GuidesTab from './tabs/GuidesTab';

export default function Admin() {
  const { user } = useAuth();
  const { notify } = useGame();

  const [tab, setTab] = useState('usage');
  const [usage, setUsage] = useState([]);
  const [members, setMembers] = useState([]);
  const [logs, setLogs] = useState([]);
  const [guides, setGuides] = useState(null);
  const [notice, setNotice] = useState({ subject: '', content: '' });
  const [range, setRange] = useState({ from: '2026-07-01', to: '2026-07-10' });
  const [latency, setLatency] = useState(null); // 리뷰 응답시간 (API-054)
  const [busy, setBusy] = useState(false);

  const isAdmin = user.role === 'ADMIN';

  useEffect(() => {
    if (!isAdmin) return;
    api.adminAiUsage(range.from, range.to).then(setUsage);
    api.getReviewLatency(range.from, range.to).then(setLatency);
    api.adminMembers().then(setMembers);
    api.adminActivityLogs().then(setLogs);
    api.adminGetGuidelines().then(setGuides);
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
      notify(`공지 발송: 성공 ${res.successCount} / 실패 ${res.failCount}`);
      setNotice({ subject: '', content: '' });
    } finally { setBusy(false); }
  };

  const saveGuide = async (level) => {
    await api.adminSaveGuideline(level, guides[level]);
    notify(`${level} 지침 저장 — 다음 리뷰부터 반영`);
  };

  return (
    <main className="app-main">
      <PageHead badge="ADMIN" badgeCls="co" title="관리자 콘솔" lead={"운영 지표, 회원, AI 리뷰 지침을 한곳에서 관리해요."} />

      <Tabs items={[['usage', 'AI 사용량'], ['members', '회원 관리'], ['logs', '활동 로그 · 공지'], ['guides', '리뷰 지침']]} value={tab} onChange={setTab} />

      {tab === 'usage' && <UsageTab usage={usage} range={range} setRange={setRange} latency={latency} />}
      {tab === 'members' && <MembersTab members={members} onStatus={changeStatus} onRole={changeRole} me={user.email} />}
      {tab === 'logs' && <LogsTab logs={logs} notice={notice} setNotice={setNotice} onSend={sendNotice} busy={busy} />}
      {tab === 'guides' && guides && <GuidesTab guides={guides} setGuides={setGuides} onSave={saveGuide} />}
    </main>
  );
}
