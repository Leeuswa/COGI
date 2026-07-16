/*
 * 회원가입 (AUTH-001~002/009, API-003/004/005, 약관 API-063/064)
 *
 * 가입 경로가 셋이라 화면 상태(mode)를 나눴다:
 *   email  ① 이메일/비밀번호/비밀번호 확인 + 약관 동의 → 인증코드 발송
 *          ② 6자리 코드 검증 → 가입 완료 → 바로 /onboarding (스킵 불가)
 *   social 카카오/GitHub 버튼 → OAuth 동의 → 프로필(이름·이메일)을 받아와서
 *          "읽기전용"으로 보여주고 약관 동의만 받는다.
 *          이름·이메일은 카카오 소유 정보라 여기서 입력/수정 자체가 없다.
 *          비밀번호도 없다(소셜 계정 보안을 따름). 이메일 인증도 생략(카카오가 이미 검증).
 *
 * 관심기술은 어느 경로든 여기서 안 받는다. 온보딩에서 받는 게 확정 흐름 (요구사항 7번).
 * 목 모드 인증코드는 000000 고정, 소셜 프로필은 mock.js 의 1건짜리 목 프로필.
 */
import { useState, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import * as api from '../../api/client';
import { TermsDialog } from '../../components/ui';
import { useAuth } from '../../context/AuthContext';

export default function Signup() {
  const { signIn } = useAuth();
  const nav = useNavigate();
  const backTo = useLocation().state?.from; // 초대 링크 등에서 온 경우 온보딩 후 복귀 목적지

  // mode: 'email'(기본) | 'social' — social 이면 profile 이 채워져 있다
  const [mode, setMode] = useState('email');
  const [profile, setProfile] = useState(null);   // { provider, name, email, githubUsername? }

  const [step, setStep] = useState(1);            // 이메일 가입 전용 (1: 입력, 2: 코드)
  const [email, setEmail] = useState('');
  const [nickname, setNickname] = useState('');   // 선택 — 비우면 서버가 이메일 앞부분으로 채움
  const [pw, setPw] = useState('');
  const [pw2, setPw2] = useState('');
  const [terms, setTerms] = useState([]);         // 약관 목록 (API-063)
  const [agreed, setAgreed] = useState([]);
  const [viewTerm, setViewTerm] = useState(null); // 약관 본문 팝업       // 체크한 약관 id들
  const [code, setCode] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  // 결제/구독 약관(PAYMENT)은 가입이 아니라 "결제 진행 시" 동의받는다 (AUTH-009 비고) → 여기선 제외
  useEffect(() => { api.getTerms().then((all) => setTerms(all.filter((t) => t.type !== 'PAYMENT'))); }, []);

  const toggleTerm = (id) =>
    setAgreed((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));

  // 전체 동의 한 방
  const allIds = terms.map((t) => t.id);
  const allOn = allIds.length > 0 && allIds.every((id) => agreed.includes(id));

  // 필수 약관 체크 여부 — 이메일/소셜 공용 검증
  const requiredOk = () => {
    const required = terms.filter((t) => t.isRequired).map((t) => t.id);
    return required.every((id) => agreed.includes(id));
  };

  /* ── 소셜 가입: 버튼 클릭 → 프로필 받아오기 ── */
  const startSocial = (provider) => {
    // 소셜 가입/로그인은 백엔드 OAuth 진입점으로 풀페이지 이동. 동의 후 백엔드가 쿠키를 세팅하고 /oauth/callback 으로 돌려보낸다.
    window.location.href = provider === 'KAKAO' ? api.kakaoOAuthUrl() : api.githubOAuthUrl();
  };

  /* ── 소셜 가입: 약관 동의 → 확정 ── */
  const finishSocial = async (e) => {
    e.preventDefault();
    setErr('');
    if (!requiredOk()) return setErr('필수 약관에 동의해주세요.');
    setBusy(true);
    try {
      const res = await api.socialSignup(profile.provider, agreed);
      await api.submitAgreements(agreed.map((id) => ({ termId: id, agreed: true })));
      // 이름·이메일·provider가 실린 user를 서버(목)가 내려준다 → 마이페이지 수정 잠금의 근거
      signIn(res.accessToken, res.user);
      nav('/onboarding', { state: { from: backTo } });
    } finally { setBusy(false); }
  };

  /* ── 이메일 가입 1단계: 입력 + 약관 → 코드 발송 ── */
  const step1 = async (e) => {
    e.preventDefault();
    setErr('');
    if (pw.length < 8) return setErr('비밀번호는 8자 이상이어야 해요.');
    if (pw !== pw2) return setErr('비밀번호 확인이 일치하지 않아요.');
    if (!requiredOk()) return setErr('필수 약관에 동의해주세요.');

    setBusy(true);
    try {
      await api.sendEmailCode(email, 'SIGNUP');
      setStep(2);
    } finally { setBusy(false); }
  };

  /* ── 이메일 가입 2단계: 코드 검증 → 가입 완료 ── */
  const step2 = async (e) => {
    e.preventDefault();
    setErr('');
    setBusy(true);
    try {
      await api.verifyEmailCode(email, code);              // 5분 만료 검증
      await api.signup(email, pw, pw2, agreed, nickname);  // 가입만 (약관 동의는 백엔드가 함께 저장, nickname 선택)
      // 가입은 여기까지. 로그인 화면으로 보내고, 로그인 시 온보딩 미완료면 온보딩으로 이동한다.
      nav('/login', { state: { signupDone: true, from: backTo } });
    } catch {
      setErr('인증코드가 맞지 않아요.');
    } finally { setBusy(false); }
  };

  /* ── 약관 체크 블록 — 이메일/소셜 폼에서 같이 씀 (FR-89~90) ── */
  const termsBlock = (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      <label className="check-row" style={{ fontWeight: 700 }}>
        <input type="checkbox" checked={allOn}
          onChange={() => setAgreed(allOn ? [] : allIds)} />
        전체 동의
      </label>
      {terms.map((t) => (
        <label key={t.id} className="check-row">
          <input type="checkbox" checked={agreed.includes(t.id)} onChange={() => toggleTerm(t.id)} />
          <span>
            [{t.isRequired ? '필수' : '선택'}]{' '}
            {/* 제목 클릭 = 본문 팝업 (체크 토글이 같이 일어나지 않게 분리) */}
            <button type="button" className="term-link"
              onClick={(e) => { e.preventDefault(); e.stopPropagation(); setViewTerm(t); }}>
              {t.title}
            </button>{' '}
            (v{t.version})
          </span>
        </label>
      ))}
    </div>
  );

  /* ── 화면 ── */

  // 소셜 가입 확정 화면: 프로필은 잠긴 채로 보여주기만, 약관만 받는다
  if (mode === 'social' && profile) {
    const from = profile.provider === 'KAKAO' ? '카카오' : 'GitHub';
    return (
      <main className="auth-wrap">
        <div className="auth-card">
          <h2>{from} 계정으로 입양 신청</h2>
          <p className="desc">
            아래 정보는 {from}에서 가져온 거라 여기서 못 바꿔요. 약관만 확인하면 끝!
          </p>

          <form className="form" onSubmit={finishSocial}>
            <div>
              <label>이름(닉네임) — {from} 제공</label>
              <input type="text" value={profile.name} disabled />
            </div>
            <div>
              <label>이메일 — {from} 제공</label>
              <input type="email" value={profile.email} disabled />
            </div>

            {termsBlock}

            {err && <p className="err">{err}</p>}
            <button className="btn co" type="submit" disabled={busy}>
              {busy ? '가입 처리 중…' : '동의하고 가입 완료'}
            </button>
            <button className="btn wh sm" type="button"
              onClick={() => { setMode('email'); setProfile(null); setAgreed([]); setErr(''); }}>
              다른 방법으로 가입하기
            </button>
          </form>
        </div>
      </main>
    );
  }

  return (
    <main className="auth-wrap">
      <div className="auth-card">
        {step === 1 ? (
          <>
            <h2>코기 입양 신청서</h2>
            <p className="desc">가입하면 알 하나가 배정됩니다. 관심기술은 다음 화면(온보딩)에서 골라요.</p>

            {/* 소셜 가입 — 이름·이메일 입력 없이 약관 동의만으로 끝나는 빠른 길 */}
            <div className="sns">
              <button className="sns-btn kakao" onClick={() => startSocial('KAKAO')} disabled={busy}>
                카카오로 회원가입
              </button>
              <button className="sns-btn github" onClick={() => startSocial('GITHUB')} disabled={busy}>
                GitHub로 회원가입
              </button>
            </div>

            <div className="divider">OR EMAIL</div>

            <form className="form" onSubmit={step1}>
              <div>
                <label>이메일</label>
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
              </div>
              <div>
                <label>닉네임 (선택 — 비우면 이메일 앞부분으로 자동)</label>
                <input type="text" value={nickname} onChange={(e) => setNickname(e.target.value)} maxLength={20} autoComplete="nickname" />
              </div>
              <div>
                <label>비밀번호 (8자 이상)</label>
                <input type="password" value={pw} onChange={(e) => setPw(e.target.value)} required autoComplete="new-password" />
              </div>
              <div>
                <label>비밀번호 확인</label>
                <input type="password" value={pw2} onChange={(e) => setPw2(e.target.value)} required autoComplete="new-password" />
              </div>

              {/* 약관 (필수 2 + 선택 1, FR-89~90) */}
              {termsBlock}

              {err && <p className="err">{err}</p>}
              <button className="btn co" type="submit" disabled={busy}>
                {busy ? '코드 발송 중…' : '이메일 인증하기'}
              </button>
            </form>

            <div className="auth-foot">
              이미 계정이 있다면 <Link to="/login">로그인</Link>
            </div>
          </>
        ) : (
          <>
            <h2>메일함을 확인하세요</h2>
            <p className="desc">{email} 로 6자리 인증코드를 보냈어요. 5분 안에 입력해주세요.</p>

            <form className="form" onSubmit={step2}>
              <div>
                <label>인증코드 6자리</label>
                <input type="text" inputMode="numeric" maxLength={6} value={code}
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                  style={{ fontFamily: 'Silkscreen, monospace', letterSpacing: 6, textAlign: 'center' }} required />
              </div>
              {err && <p className="err">{err}</p>}
              <button className="btn co" type="submit" disabled={busy || code.length !== 6}>
                {busy ? '검증 중…' : '가입 완료'}
              </button>
              <button className="btn wh sm" type="button" onClick={() => api.sendEmailCode(email, 'SIGNUP')}>
                코드 재발송
              </button>
            </form>
          </>
        )}
      </div>
      <TermsDialog term={viewTerm} onClose={() => setViewTerm(null)} />
    </main>
  );
}
