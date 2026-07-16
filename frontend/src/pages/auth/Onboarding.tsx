/*
 * 온보딩 (AUTH-007~008, API-011/012, FR-15~17)
 * 최초 로그인 시 "무조건" 거쳐야 하는 화면. 라우터 가드가 스킵을 막는다.
 *   ① 서비스 이용 가이드 확인 (guideConfirmed)
 *   ② 코딩 수준 선택 (BEGINNER / INTERMEDIATE / ADVANCED)
 *   ③ 관심기술 선택 (1개 이상)
 * 제출하면 onboardingCompleted:true 로 패치 → 대시보드 입장.
 * 여기서 고른 수준이 리뷰 프롬프트 지침(ADM-004)과 학습카드 난이도에 쓰인다.
 */
import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import * as api from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import InterestPicker from '../../components/InterestPicker';
import { SPR } from '../../assets/sprites';

const LEVELS = [
  { code: 'BEGINNER', name: '초급', desc: '용어부터 풀어서. 왜 문제인지 → 어떻게 고치는지 순서로 설명받아요.' },
  { code: 'INTERMEDIATE', name: '중급', desc: '트레이드오프와 대안까지. 성능 영향은 수치로 받아요.' },
  { code: 'ADVANCED', name: '고급', desc: '설계 관점 위주. 크로스 파일 영향과 아키텍처 리스크 중심.' },
];

export default function Onboarding() {
  // (아래 useEffect) 서버 기준으로 이미 온보딩을 마친 계정이면 바로 대시보드로 —
  // 다른 기기에서 끝냈는데 이 기기 캐시가 낡은 경우를 잡는다 (API-011)
  const { patchUser } = useAuth();
  const nav = useNavigate();
  const backTo = useLocation().state?.from; // 초대 링크에서 온 가입자는 완료 후 그 초대 화면으로

  useEffect(() => {
    api.getOnboardingStatus().then((r) => { if (r.completed) nav('/app', { replace: true }); });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const [step, setStep] = useState(1);
  const [guideOk, setGuideOk] = useState(false);
  const [level, setLevel] = useState(null);
  const [interests, setInterests] = useState([]);
  const [busy, setBusy] = useState(false);

  const toggle = (t) =>
    setInterests((prev) => (prev.includes(t) ? prev.filter((x) => x !== t) : [...prev, t]));

  const finish = async () => {
    setBusy(true);
    try {
      await api.submitOnboarding(level, interests, true);
      // 라우터 가드(RequireOnboarded)가 이 플래그를 본다
      patchUser({ onboardingCompleted: true, guideConfirmed: true, level, interests });
      nav(backTo || '/app');
    } finally { setBusy(false); }
  };

  return (
    <main className="app-main" style={{ maxWidth: 720 }}>
      <div className="page-head">
        <span className="badge co">ONBOARDING {step}/3</span>
        <h2 className="sec-title">코기와 첫 인사</h2>
        <p className="sec-lead" style={{ marginBottom: 0 }}>
          3분이면 끝나요. 이 설문으로 리뷰 말투와 학습카드 난이도가 정해집니다. (건너뛰기 없음!)
        </p>
      </div>

      {/* ① 이용 가이드 */}
      {step === 1 && (
        <div className="panel">
          <h3>COGI는 이렇게 돌아가요</h3>
          <div style={{ fontSize: 13.5, lineHeight: 2, color: 'var(--sub)' }}>
            <p>1. GitHub 레포를 연동하면 PR을 올릴 때마다 AI가 자동으로 리뷰합니다.</p>
            <p>2. 같은 실수가 3번 반복되면 <b>약점</b>으로 승격되고, 학습카드가 만들어져요.</p>
            <p>3. 카드는 신호등 등급: 빨강(시작) → 노랑(정답 3회) → 초록(정답 7회, 해결).</p>
            <p>4. 퀴즈를 <b>제출만 해도</b> 연속 학습일이 채워지고, 코기가 자랍니다.<br />정답이면 코인 보너스까지 받아요.</p>
            <p>5. AI 기능(리뷰·카드·퀴즈·스킬추천)은 일일 크레딧을 씁니다. FREE는 매일 20개.</p>
          </div>
          <label className="check-row mt18">
            <input type="checkbox" checked={guideOk} onChange={(e) => setGuideOk(e.target.checked)} />
            이용 가이드를 확인했어요
          </label>
          <button className="btn co mt18" disabled={!guideOk} onClick={() => setStep(2)}>
            다음
          </button>
        </div>
      )}

      {/* ② 수준 선택 */}
      {step === 2 && (
        <div className="panel">
          <h3>코딩 수준을 골라주세요</h3>
          <div className="panel-grid c3">
            {LEVELS.map((l) => (
              <button key={l.code}
                className={`pick ${level === l.code ? 'on' : ''}`}
                style={{ textAlign: 'left', padding: 16, lineHeight: 1.8 }}
                onClick={() => setLevel(l.code)}
                aria-pressed={level === l.code}
              >
                <b style={{ display: 'block', fontSize: 15 }}>{l.name}</b>
                <span style={{ fontSize: 12 }}>{l.desc}</span>
              </button>
            ))}
          </div>
          <p style={{ marginTop: 14, fontSize: 12, color: 'var(--sub)' }}>나중에 마이페이지에서 언제든 바꿀 수 있어요.</p>
          <div style={{ display: 'flex', gap: 10, marginTop: 16 }}>
            <button className="btn wh sm" onClick={() => setStep(1)}>이전</button>
            <button className="btn co sm" disabled={!level} onClick={() => setStep(3)}>다음</button>
          </div>
        </div>
      )}

      {/* ③ 관심기술 */}
      {step === 3 && (
        <div className="panel">
          <h3>관심 기술을 골라주세요 (1개 이상, 분야 넘나들며 OK)</h3>
          <InterestPicker value={interests} onToggle={toggle} />
          <div style={{ display: 'flex', gap: 10, marginTop: 22, alignItems: 'center' }}>
            <button className="btn wh sm" onClick={() => setStep(2)}>이전</button>
            <button className="btn co" disabled={interests.length === 0 || busy} onClick={finish}>
              {busy ? '알 배정 중…' : '시작하기 🥚'}
            </button>
            <img src={SPR.egg} alt="" className="ml-auto" style={{ height: 34 }} />
          </div>
        </div>
      )}
    </main>
  );
}
