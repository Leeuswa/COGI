/*
 * 마이페이지 컨테이너 (AUTH-006, API-009/010/013 + TOTP API-006-1 + 약관 현황)
 * 상태와 저장/연동/OTP 핸들러는 여기, 화면은 tabs/ 아래 파일 하나씩.
 */
import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import * as api from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { useGame } from '../../context/GameContext';
import { PageHead, Tabs } from '../../components/ui';
import ProfileTab from './tabs/ProfileTab';
import GithubTab from './tabs/GithubTab';
import SecurityTab from './tabs/SecurityTab';
import TermsTab from './tabs/TermsTab';

export default function MyPage() {
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

  // GitHub 연동 리다이렉트 결과 처리 — 백엔드 콜백이 ?linked=1 또는 ?error=... 로 되돌려보낸다
  const [params, setParams] = useSearchParams();
  useEffect(() => {
    if (params.get('linked')) {
      notify('GitHub 연동 완료!');
      api.getProfile().then((u) => patchUser({ githubUsername: u.githubUsername }));
      setTab('github');
      setParams({}, { replace: true });   // URL에서 쿼리 정리
    } else if (params.get('error')) {
      const e = params.get('error');
      notify(e === 'github_already_linked'
        ? '이미 다른 계정에 연결된 GitHub이에요.'
        : 'GitHub 연동에 실패했어요. 다시 시도해주세요.');
      setTab('github');
      setParams({}, { replace: true });
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps -- 최초 1회만

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

  // GitHub 연동 — 백엔드 authorize로 풀페이지 이동(OAuth 시작).
  // 동의 후 백엔드 콜백이 /app/my?linked=1 (또는 ?error=...) 로 되돌려보낸다.
  const linkGh = () => { window.location.href = '/api/users/me/github/authorize'; };

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
