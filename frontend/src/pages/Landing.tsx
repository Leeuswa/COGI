/*
 * 랜딩 페이지 — 원본 와이어프레임(COGI_최종_다마고치_아케이드.html)을 1:1로 옮김.
 * 바뀐 건 CTA뿐: "GitHub으로 시작" → 회원가입/로그인 라우팅 (요구사항 7).
 * 섹션 순서/문구/애니메이션은 원본 그대로 유지.
 */
import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import CorgiDevice from '../components/CorgiDevice';
import { Reveal } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { SPR } from '../assets/sprites';

export default function Landing() {
  return (
    <>
      <Hero />
      <Marquee />
      <InfoSections />
      <FinalCta />
    </>
  );
}

// 서비스 안내 섹션 묶음 — 랜딩과 (로그인 후) 대시보드 하단에서 공용
export function InfoSections() {
  return (
    <>
      <ReviewSection />
      <LoopSection />
      <CareSection />
      <PetSection />
      <PlanSection />
    </>
  );
}

function Hero() {
  return (
    <header className="hero">
      {/* 흘러가는 구름 3개. 속도/딜레이 원본값 */}
      <div className="cloud" style={{ top: '12%', animationDuration: '52s' }} />
      <div className="cloud" style={{ top: '38%', animationDuration: '74s', animationDelay: '-30s' }} />
      <div className="cloud" style={{ top: '72%', animationDuration: '63s', animationDelay: '-15s' }} />

      <div className="hero-copy">
        <span className="lv">POCKET CORGI · LV.UP EVERY DAY</span>
        <h1>
          어제 까인 코드리뷰가,<br />
          <span className="hl">오늘 코기 사료가 됩니다</span>
        </h1>
        <p className="sub">
          같은 실수가 세 번이면 약점으로 기록되고,<br />
          약점은 5분 미션이, 미션은 코인이 됩니다.<br />
          그 코인으로 코기 밥을 사 주세요.
        </p>
        <div className="cta-row">
          <Link className="btn co" to="/signup">회원가입하고 시작</Link>
          <Link className="btn" to="/login">로그인</Link>
          <Link className="btn wh" to="/guest">비로그인 체험</Link>
        </div>
        <p className="hint">▶ 진짜로 작동합니다. 퀴즈 하나 풀고 알부터 깨 보세요.</p>
      </div>

      <CorgiDevice />
    </header>
  );
}

function Marquee() {
  const item = (
    <span>
      REVIEW <b className="dot">▸</b> 약점 데이터화 <b className="dot">▸</b> 맞춤 학습{' '}
      <b className="dot">▸</b> 코기 성장 <b className="dot">▸</b> COGI · CODE GUIDE <b className="dot">★</b>
    </span>
  );
  return (
    <div className="marquee" aria-hidden="true">
      <div className="track">{item}{item}</div>
    </div>
  );
}

function ReviewSection() {
  return (
    <section id="review">
      <div className="wrap">
        <span className="badge">CODE REVIEW</span>
        <h2 className="sec-title">머지 버튼을 누르기 전에, 코기가 먼저 읽습니다</h2>
        <p className="sec-lead">
          버그, 컨벤션, 성능, 보안, 가독성. 다섯 갈래로 뜯어보고 중요한 것부터 줄을 세웁니다.
          새벽 3시에 올린 커밋도 빠짐없이.
        </p>
        <div className="rev-grid">
          <Reveal className="rev-panel">
            <div className="rev-head"><span>pull_request #42</span><span>review</span></div>
            <div className="rev-line"><b className="sev hi">BUG · HIGH</b>user.ts:88 · null 체크 누락</div>
            <div className="rev-line"><b className="sev mid">PERF · MID</b>render.tsx:51 · 불필요한 리렌더</div>
            <div className="rev-line"><b className="sev low">CONV · LOW</b>date.ts:12 · 네이밍 컨벤션</div>
            <div className="rev-foot">"null 안전성" 3회 반복 → 약점 승격 · 내일 미션으로 전환</div>
          </Reveal>
          <Reveal as="ul" className="rev-list" delay={80}>
            <li><b>5종 카테고리</b>리뷰어마다 다른 기준 대신, 언제나 같은 다섯 개의 눈으로 봅니다.</li>
            <li><b>심각도 3단계 라인 코멘트</b>당장 고칠 것, 이번 주에 고칠 것, 알아만 둘 것. 순서까지 정해서 라인에 답니다.</li>
            <li><b>"의도한 코드" 버튼</b>일부러 그렇게 짠 코드라면 버튼 한 번. 두 번 다시 잔소리하지 않습니다.</li>
            <li><b>결과 내보내기</b>팀 회고 때 쓰기 좋게 MD, PDF, Notion으로 바로 뽑아 갑니다.</li>
          </Reveal>
        </div>
      </div>
    </section>
  );
}

function LoopSection() {
  const steps = [
    ['STEP 1', '리뷰 · 학습', '리뷰에서 나온 내 약점으로 5분짜리 미션이 만들어집니다. 남의 공부가 아니라 내 코드 이야기.'],
    ['STEP 2', '코인 획득', '미션을 끝내면 코인, 퀴즈를 맞히면 더 많이. 연속 학습을 지키면 보너스까지.'],
    ['STEP 3', '코기 돌보기', '밥 주고, 산책 가고, 치워 주고. 코인 쓸 곳은 한 마리뿐입니다.'],
    ['STEP 4', '함께 성장', '경험치가 차면 몸집이 커지고 반다나도 생깁니다. 약점 하나를 극복하면 크게 오릅니다.'],
  ];
  return (
    <section className="loop" id="learn">
      <div className="wrap">
        <span className="badge">GROWTH LOOP</span>
        <h2 className="sec-title">미루던 공부를, 코기가 조릅니다</h2>
        <p className="sec-lead">
          인강 결제만 하고 안 듣는 사람, 저희 팀에도 있습니다. 그래서 공부를 시키는 대신
          굶는 코기를 보여 주기로 했습니다.
        </p>
        <div className="loop-grid">
          {steps.map(([n, t, d], i) => (
            <Reveal className="l-card" key={n} delay={i * 80}>
              <span className="n">{n}</span><h3>{t}</h3><p>{d}</p>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}

function CareSection() {
  const cares = [
    ['🍖', '먹이주기', '= 일일 학습 미션', '오늘 학습카드를 끝내야 오늘 밥값이 나옵니다. 자정이 지나면 미션도 밥때도 리셋. 코기는 기다려 주지만 배는 고픕니다.'],
    ['🐾', '산책하기', '= 연속 학습 유지', '학습이 하루씩 이어질 때마다 산책 코스가 길어져요. 기록이 끊기면 제일 시무룩해지는 건 코기 쪽입니다.'],
    ['🧹', '청소하기', '= 약점 정리', '같은 실수가 세 번 쌓이면 약점이 되고, 코기 집도 지저분해집니다. 약점을 학습으로 털어내면 집도 같이 반짝해집니다.'],
  ];
  return (
    <section id="care">
      <div className="wrap">
        <span className="badge">CARE = STUDY</span>
        <h2 className="sec-title">귀여움에도 근거가 있습니다</h2>
        <p className="sec-lead">버튼 세 개는 장식이 아닙니다. 서비스의 핵심 기능과 하나씩 묶여 있습니다.</p>
        <div className="care-grid">
          {cares.map(([em, t, map, d], i) => (
            <Reveal className="care" key={t} delay={i * 80}>
              <div className="head"><span className="em">{em}</span><h3>{t}</h3></div>
              <span className="map">{map}</span>
              <p>{d}</p>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}

function PetSection() {
  // 단계 카드의 스프라이트는 기기(LCD)와 같은 원본 도트를 재사용
  const stages: [string, string, string, JSX.Element][] = [
    [SPR.egg, 'sm', '알', <>퀴즈 하나면 깨어납니다</>],
    [SPR.baby1, 'sm', 'Lv.1 아기 코기', <>XP 1~49<br />첫 퀴즈와 함께 부화</>],
    [SPR.kid1, '', 'Lv.2 어린 코기', <>XP 50~149<br />귀가 쫑긋 서는 시기</>],
    [SPR.adult1, 'lg', 'Lv.3 성견 코기', <>XP 150~299<br />늠름한 몸집 완성</>],
    [SPR.boss, 'lg', 'Lv.4 대장 코기', <>XP 300+<br />빨간 반다나의 위엄</>],
  ];
  return (
    <section id="pet" style={{ background: 'var(--sky2)', borderTop: '3px solid var(--navy)', borderBottom: '3px solid var(--navy)' }}>
      <div className="wrap">
        <span className="badge">TAMAGOTCHI</span>
        <h2 className="sec-title">알에서 대장 코기까지</h2>
        <p className="sec-lead">
          레벨이 오르면 몸집과 귀, 장비가 달라집니다. 매주 월요일 10시 주간 리포트에는
          실력 그래프 옆에 코기 근황이 같이 붙습니다.
        </p>
        <div className="g-grid">
          {stages.map(([src, size, t, d], i) => (
            <Reveal className="g-card" key={t} delay={i * 80}>
              <div className="box"><img className={size} src={src} alt={t} /></div>
              <h3>{t}</h3><p>{d}</p>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}

function PlanSection() {
  const { user } = useAuth();                 // 로그인 상태면 현재 플랜을 표시
  const cur = user?.planName;                 // 'FREE' | 'PRO' | 'MAX' | undefined(비로그인)
  const PLANS = [
    { name: 'FREE', model: 'Gemini 3 Pro · Grok 4', credit: 20,
      feats: ['기본 카테고리 리뷰(버그·컨벤션)', '개념 설명 학습카드', '로컬 체험 모드'] },
    { name: 'PRO', model: 'Claude Sonnet 5', credit: 40, mid: true,
      feats: ['5종 카테고리 + 보안 취약점 분석', '예제 코드 포함 학습카드', '주간 성장 리포트(월 10시)'] },
    { name: 'MAX', model: 'Claude Opus 4.8', credit: 70,
      feats: ['크로스 파일 심층 분석', '예제 + 심화 퀴즈', '팀 초대 · 함께 성장'] },
  ];
  return (
    <section id="plan">
      <div className="wrap">
        <span className="badge">SELECT YOUR PLAYER</span>
        <h2 className="sec-title">플랜 선택</h2>
        <p className="sec-lead">
          위로 갈수록 리뷰가 깊어지고(모델 등급), 하루에 더 많이 씁니다(크레딧).
          크레딧은 자정에 초기화됩니다.
        </p>
        <div className="chars">
          {PLANS.map((p, i) => {
            const mine = cur === p.name;        // 현재 이용 중인 플랜
            return (
              <Reveal className={`char ${p.mid ? 'mid' : ''} ${mine ? 'current' : ''}`} key={p.name} delay={i * 80}>
                {mine && <div className="plan-flag">현재 이용 중</div>}
                <div className="pname">{p.name}</div>
                <div className="model">{p.model}</div>
                <div className="credit">{p.credit}<small>credits / day · 자정 초기화</small></div>
                <ul>{p.feats.map((f) => <li key={f}>{f}</li>)}</ul>
                {/* 비로그인: 가입 유도 / 로그인: 현재 플랜은 '사용 중', 그 외는 요금제 페이지로 */}
                {!user ? (
                  <Link className={`btn ${p.mid ? '' : 'wh'}`} to="/signup">
                    {p.name === 'FREE' ? '무료로 시작' : `${p.name} 시작`}
                  </Link>
                ) : mine ? (
                  <span className="btn wh" style={{ opacity: .6, cursor: 'default' }}>사용 중</span>
                ) : (
                  <Link className={`btn ${p.mid ? '' : 'wh'}`} to="/app/plan">플랜 변경 →</Link>
                )}
              </Reveal>
            );
          })}
        </div>
        <p className="plan-note">* AWS Bedrock 기반. 어느 플랜이든 코기는 같이 삽니다.</p>
      </div>
    </section>
  );
}

function FinalCta() {
  const starsRef = useRef(null);
  // 반짝이 별 14개를 랜덤 배치 (원본 스크립트 이식)
  useEffect(() => {
    const box = starsRef.current;
    for (let i = 0; i < 14; i++) {
      const s = document.createElement('i');
      s.style.left = 4 + Math.random() * 92 + '%';
      s.style.top = 8 + Math.random() * 84 + '%';
      s.style.animationDelay = Math.random() * 1.6 + 's';
      box.appendChild(s);
    }
    return () => { box.innerHTML = ''; };
  }, []);

  return (
    <section className="final">
      <div className="stars" ref={starsRef} />
      <div className="wrap">
        <h2>오늘 미션을 끝내면,<br />코기가 꼬리를 흔듭니다</h2>
        <p>가입 1분. 다음 PR부터 코기가 함께 뜁니다.</p>
        <Link className="btn" to="/signup">지금 시작하기</Link>
      </div>
    </section>
  );
}
