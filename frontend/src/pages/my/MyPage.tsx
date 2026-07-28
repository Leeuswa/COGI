/*
 * 마이페이지 컨테이너 (AUTH-006, API-009/010/013 + TOTP API-006-1 + 약관 현황)
 * 상태와 저장/연동/OTP 핸들러는 여기, 화면은 tabs/ 아래 파일 하나씩.
 */
import { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import * as api from '../../api/client';
import { isValidPassword, PW_HINT } from '../../utils/password';
import { useAuth } from '../../context/AuthContext';
import { useGame } from '../../context/GameContext';
import { PageHead, Tabs } from '../../components/ui';
import ProfileTab from './tabs/ProfileTab';
import GithubTab from './tabs/GithubTab';
import SecurityTab from './tabs/SecurityTab';
import TermsTab from './tabs/TermsTab';
import NoticeTab from './tabs/NoticeTab';
import useIsMobile from '../../hooks/useIsMobile';
import MobileMy from '../mobile/MobileMy';

const WITHDRAW_PHRASE = '탈퇴하겠습니다';

// 폰이면 데스크톱 본체를 아예 mount하지 않는다.
// 한 컴포넌트 안에서 갈랐더니 조건부 return 위의 useEffect가 그대로 돌아 같은 API를 두 번 때렸다.
// getWeaknessStats처럼 조회할 때마다 통계를 지우고 다시 넣는 API는 두 번 겹치면 한쪽이 빈 목록을 받는다.
export default function MyPage() {
  return useIsMobile() ? <MobileMy /> : <DesktopMyPage />;
}

function DesktopMyPage() {
  const { user, patchUser, signOut } = useAuth();
  const { notify } = useGame();
  const navigate = useNavigate();

  const [tab, setTab] = useState('profile');
  const [wdOpen, setWdOpen] = useState(false);      // 회원탈퇴 모달
  const [wdText, setWdText] = useState('');         // 확인 문구 입력값
  const [wdBusy, setWdBusy] = useState(false);
  const [name, setName] = useState(user.name || '');
  const [level, setLevel] = useState(user.level || 'BEGINNER');
  const [interests, setInterests] = useState(user.interests || []);
  const [totp, setTotp] = useState(null); // setup 응답 { secret }
  const [terms, setTerms] = useState([]);
  const [agreedIds, setAgreedIds] = useState([]); // 내가 동의한 약관 id
  const [agreedVers, setAgreedVers] = useState([]); // [{termId, agreedVersion}] — 개정 여부 판정용
  const [busy, setBusy] = useState(false);
  const [pwOpen, setPwOpen] = useState(false);
  const [pw, setPw] = useState({ cur: '', next: '', confirm: '' });
  const [pwBusy, setPwBusy] = useState(false);
  const [linkError, setLinkError] = useState(''); // GitHub 연동 실패 안내 (카드에 표시)

  // 카카오/GitHub 가입자는 이름(닉네임)·이메일이 소셜 계정 소유 정보라 여기서 못 바꾼다.
  const isSocial = user.provider === 'KAKAO' || user.provider === 'GITHUB';
  const providerName = user.provider === 'KAKAO' ? '카카오' : user.provider === 'GITHUB' ? 'GitHub' : null;

  const loadAgreements = () => {
    api.getMyAgreements().then(setAgreedIds).catch(() => setAgreedIds([]));
    api.getMyAgreementVersions().then(setAgreedVers).catch(() => setAgreedVers([]));
  };
  useEffect(() => {
    api.getTerms().then(setTerms);
    loadAgreements();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

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
      setLinkError(e === 'github_already_linked'
        ? '이미 연결된 GitHub 계정입니다.'
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
      // 소셜 유저는 닉네임이 잠겨있어 값이 안 바뀌지만, 백엔드가 nickname 필수라 기존 값 그대로 보낸다
      await api.updateProfile(name.trim(), level, interests);
      patchUser({ name: name.trim(), level, interests });
      notify('프로필이 저장됐어요. 다음 리뷰부터 바로 반영돼요');
    } catch (ex) {
      notify(ex.message || '저장에 실패했어요. 잠시 후 다시 시도해주세요');
    } finally { setBusy(false); }
  };

  // GitHub 연동 — 백엔드 authorize로 풀페이지 이동(OAuth 시작).
  // 동의 후 백엔드 콜백이 /app/my?linked=1 (또는 ?error=...) 로 되돌려보낸다.
  const linkGh = () => { window.location.href = '/api/users/me/github/authorize'; };

  // 비밀번호 변경 — 프론트에서 형식 검증, 현재 비번 대조는 서버(목: 1234)
  const changePw = async () => {
    if (!isValidPassword(pw.next)) return notify(PW_HINT);
    if (pw.next !== pw.confirm) return notify('새 비밀번호가 서로 달라요. 다시 확인해주세요');
    setPwBusy(true);
    try {
      await api.changePassword(pw.cur, pw.next, pw.confirm);
      notify('비밀번호를 바꿨어요. 다음 로그인부터 적용됩니다');
      setPw({ cur: '', next: '', confirm: '' });
      setPwOpen(false);
    } catch {
      notify('현재 비밀번호가 맞지 않아요');
    } finally { setPwBusy(false); }
  };

  // 1단계: 시크릿/QR 발급만. 활성화는 코드 검증 후(enableTotp)에.
  const setupTotp = async () => {
    setBusy(true);
    try {
      setTotp(await api.totpSetup());
    } catch (ex) {
      notify(ex.message || 'OTP 설정에 실패했어요');
    } finally { setBusy(false); }
  };

  // 2단계: 인증앱 6자리 검증 → 서버가 통과시켜야 실제 활성화
  const enableTotp = async (code) => {
    setBusy(true);
    try {
      await api.totpEnable(code);
      patchUser({ totpEnabled: true });
      setTotp(null);
      notify('2차 인증이 켜졌어요. 다음 로그인부터 6자리를 물어봅니다');
    } catch (ex) {
      notify(ex.message || '인증 코드가 올바르지 않아요');
    } finally { setBusy(false); }
  };

  // 회원탈퇴 — 문구 일치해야 진행. 팀장인 팀은 서버가 첫 팀원에게 위임/해체. 완료되면 로그아웃 후 홈으로.
  const doWithdraw = async () => {
    if (wdText.trim() !== WITHDRAW_PHRASE) return;
    setWdBusy(true);
    try {
      await api.withdraw(wdText.trim());
      await signOut();
      navigate('/', { replace: true });
      notify('회원 탈퇴가 완료됐어요. 이용해주셔서 감사합니다');
    } catch (ex) {
      notify(ex.message || '탈퇴 처리에 실패했어요. 잠시 후 다시 시도해주세요');
      setWdBusy(false);
    }
  };

  return (
    <main className="app-main">
      <PageHead badge="MY PAGE" title="마이페이지" lead={user.name ? `${user.name} · ${user.email}` : user.email} />

      <Tabs items={[['profile', '프로필'], ['github', 'GitHub 연동'], ['security', '보안(OTP)'], ['terms', '약관'], ['notice', '공지사항']]} value={tab} onChange={setTab} />

      {tab === 'profile' && (
        <ProfileTab user={user} isSocial={isSocial} providerName={providerName}
          name={name} setName={setName} level={level} setLevel={setLevel}
          interests={interests} toggle={toggle} onSave={saveProfile} busy={busy}
          pw={pw} setPw={setPw} pwOpen={pwOpen} setPwOpen={setPwOpen} onChangePw={changePw} pwBusy={pwBusy} />
      )}
      {tab === 'github' && <GithubTab user={user} onLink={linkGh} busy={busy} error={linkError} />}
      {tab === 'security' && <SecurityTab user={user} totp={totp} onSetup={setupTotp} onEnable={enableTotp} busy={busy} />}
      {tab === 'terms' && <TermsTab terms={terms} agreedIds={agreedIds} agreedVers={agreedVers} onReagreed={loadAgreements} />}
      {tab === 'notice' && <NoticeTab />}

      {/* 회원탈퇴 — 하단 위험구역. 프로필 탭에서만 노출 */}
      {tab === 'profile' && (
        <div className="danger-zone">
          <div>
            <b>회원 탈퇴</b>
            <p>탈퇴하면 개인정보는 삭제되고 되돌릴 수 없어요. 작성한 리뷰 기록은 '탈퇴한 회원'으로 남습니다.</p>
          </div>
          <button className="btn danger sm" onClick={() => { setWdText(''); setWdOpen(true); }}>회원 탈퇴</button>
        </div>
      )}

      {wdOpen && (
        <div className="modal-mask" onClick={() => !wdBusy && setWdOpen(false)}>
          <div className="modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <h3>정말 탈퇴하시겠어요?</h3>
            <ul style={{ fontSize: 13.5, lineHeight: 1.9, margin: '14px 0 6px', paddingLeft: 18 }}>
              <li>개인정보(이메일·닉네임·소셜 연동)가 삭제돼요.</li>
              <li>회원님이 <b>팀장인 팀</b>은 가장 먼저 합류한 팀원에게 <b>자동 위임</b>돼요.<br />(팀원이 없으면 팀이 해체돼요)</li>
              <li>작성한 리뷰·PR 기록은 <b>'탈퇴한 회원'</b>으로 남아요.</li>
            </ul>
            <p style={{ fontSize: 13.5, margin: '16px 0 8px' }}>
              계속하려면 아래에 <b>{WITHDRAW_PHRASE}</b> 를 정확히 입력해주세요.
            </p>
            <input className="input" value={wdText} onChange={(e) => setWdText(e.target.value)}
              placeholder={WITHDRAW_PHRASE} disabled={wdBusy} autoFocus />
            <div className="row" style={{ gap: 8, marginTop: 18, justifyContent: 'flex-end' }}>
              <button className="btn wh sm" onClick={() => setWdOpen(false)} disabled={wdBusy}>취소</button>
              <button className="btn danger sm" onClick={doWithdraw}
                disabled={wdBusy || wdText.trim() !== WITHDRAW_PHRASE}>
                {wdBusy ? '처리 중…' : '탈퇴하기'}
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}