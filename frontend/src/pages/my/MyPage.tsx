/*
 * 마이페이지 컨테이너 (AUTH-006, API-009/010/013 + TOTP API-006-1 + 약관 현황)
 * 상태와 저장/연동/OTP 핸들러는 여기, 화면은 tabs/ 아래 파일 하나씩.
 */
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import * as api from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { useGame } from '../../context/GameContext';
import { PageHead, Tabs } from '../../components/ui';
import ProfileTab from './tabs/ProfileTab';
import GithubTab from './tabs/GithubTab';
import SecurityTab from './tabs/SecurityTab';
import TermsTab from './tabs/TermsTab';

export default function MyPage() {
  const nav = useNavigate();
  const { user, patchUser } = useAuth();
  const { notify } = useGame();

  const [tab, setTab] = useState('profile');
  const [name, setName] = useState(user.name || '');
  const [level, setLevel] = useState(user.level || 'BEGINNER');
  const [interests, setInterests] = useState(user.interests || []);
  const [totp, setTotp] = useState(null); // setup 응답 { secret }
  const [terms, setTerms] = useState([]);
  const [busy, setBusy] = useState(false);
  const [pwOpen, setPwOpen] = useState(false);
  const [pw, setPw] = useState({ cur: '', next: '', confirm: '' });
  const [pwBusy, setPwBusy] = useState(false);

  // 카카오/GitHub 가입자는 이름(닉네임)·이메일이 소셜 계정 소유 정보라 여기서 못 바꾼다.
  const isSocial = user.provider === 'KAKAO' || user.provider === 'GITHUB';
  const providerName = user.provider === 'KAKAO' ? '카카오' : user.provider === 'GITHUB' ? 'GitHub' : null;

  useEffect(() => { api.getTerms().then(setTerms); }, []);

  const toggle = (t) =>
    setInterests((prev) => (prev.includes(t) ? prev.filter((x) => x !== t) : [...prev, t]));

  const saveProfile = async () => {
    if (!isSocial && !name.trim()) return notify('닉네임을 입력해주세요');
    if (interests.length === 0) return notify('관심기술은 1개 이상 골라주세요');
    setBusy(true);
    try {
      // 소셜 가입자는 이름을 안 보낸다 — 서버도 어차피 거절하지만 요청 자체를 안 만드는 게 맞다
      await api.updateProfile(isSocial ? undefined : name.trim(), level, interests);
      patchUser({ ...(isSocial ? {} : { name: name.trim() }), level, interests });
      notify('저장 완료. 다음 리뷰부터 바로 반영돼요');
    } finally { setBusy(false); }
  };

  const linkGh = async () => {
    setBusy(true);
    try {
      const res = await api.linkGithub(); // 실서버는 OAuth 리다이렉트, 목은 즉시 성공
      patchUser({ githubUsername: res.githubUsername });
      notify(`@${res.githubUsername} 연동 완료!`);
      // 이메일 초대를 받아둔 계정이면 — 연동이 끝났으니 팀 수락 화면으로 안내 (요구 #11)
      const myTeam = await api.getMyTeam(user.loginId);
      if (myTeam.none && myTeam.invite) {
        notify('이제 팀 초대를 수락할 수 있어요. 팀 페이지로 이동합니다');
        nav('/app/team');
      }
    } finally { setBusy(false); }
  };

  // 비밀번호 변경 — 프론트에서 형식 검증, 현재 비번 대조는 서버(목: 1234)
  const changePw = async () => {
    if (pw.next.length < 8) return notify('새 비밀번호는 8자 이상이어야 해요');
    if (pw.next !== pw.confirm) return notify('새 비밀번호가 서로 달라요. 다시 확인해주세요');
    setPwBusy(true);
    try {
      await api.changePassword(pw.cur, pw.next);
      notify('비밀번호를 바꿨어요. 다음 로그인부터 적용됩니다');
      setPw({ cur: '', next: '', confirm: '' });
      setPwOpen(false);
    } catch {
      notify('현재 비밀번호가 맞지 않아요');
    } finally { setPwBusy(false); }
  };

  const setupTotp = async () => {
    setBusy(true);
    try {
      const res = await api.totpSetup();
      setTotp(res);
      patchUser({ totpEnabled: true });
    } finally { setBusy(false); }
  };

  return (
    <main className="app-main">
      <PageHead badge="MY PAGE" title="마이페이지" lead={user.name ? `${user.name} · ${user.email}` : user.email} />

      <Tabs items={[['profile', '프로필'], ['github', 'GitHub 연동'], ['security', '보안(OTP)'], ['terms', '약관']]} value={tab} onChange={setTab} />

      {tab === 'profile' && (
        <ProfileTab user={user} isSocial={isSocial} providerName={providerName}
          name={name} setName={setName} level={level} setLevel={setLevel}
          interests={interests} toggle={toggle} onSave={saveProfile} busy={busy}
          pw={pw} setPw={setPw} pwOpen={pwOpen} setPwOpen={setPwOpen} onChangePw={changePw} pwBusy={pwBusy} />
      )}
      {tab === 'github' && <GithubTab user={user} onLink={linkGh} busy={busy} />}
      {tab === 'security' && <SecurityTab user={user} totp={totp} onSetup={setupTotp} busy={busy} />}
      {tab === 'terms' && <TermsTab terms={terms} />}
    </main>
  );
}
