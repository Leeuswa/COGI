/*
 * 랜딩 페이지 — 원본 와이어프레임(COGI_최종_다마고치_아케이드.html)을 1:1로 옮김.
 * 바뀐 건 CTA뿐: "GitHub으로 시작" → 회원가입/로그인 라우팅 (요구사항 7).
 * 섹션 순서/문구/애니메이션은 원본 그대로 유지.
 */
import { useEffect, useRef, useState, type JSX } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../api/client';
import { PLAN_FALLBACK, PLAN_FOOTNOTE, PLAN_NOTE, addedModels } from '../data/plans';
import CorgiDevice from '../components/CorgiDevice';
import { Reveal } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import useIsMobile from '../hooks/useIsMobile';
import MobileLanding from './mobile/MobileLanding';
import { SPR } from '../assets/sprites';
import Dashboard from './Dashboard';

export default function Landing() {
  // 로그인했으면 홍보 페이지가 아니라 대시보드가 홈이다. 아래는 푸터로 끝난다
  const { user } = useAuth();
  // 폰은 데스크톱 섹션을 접는 대신 화면을 따로 그린다 (분기는 여기 래퍼가 한다)
  const isMobile = useIsMobile();
  if (user) return <Dashboard />;
  if (isMobile) return <MobileLanding />;

  // .lp = 로그인 전 랜딩만. 화면 하나가 스냅 한 칸이 된다(landing.css).
  // 대시보드도 InfoSections를 쓰므로, 래퍼를 씌우는 이 자리에서만 스냅이 붙는다
  return (
    <div className="lp">
      <Hero />
      {/* 화면 배분은 콘텐츠 양으로 정한다. 전부 묶으면 섹션당 412px밖에 안 남아
          글씨를 줄여야 하고, 전부 단독이면 작은 섹션에 여백만 남는다.
          많은 것(리뷰·루프·플랜)은 단독, 작은 것(케어·성장단계)만 묶는다 */}
      <ReviewSection />
      <div className="lp-duo"><LoopSection /><CareSection /></div>
      <PetSection />
      <div className="lp-duo lp-last"><PlanSection /><FinalCta /></div>
      <SnapNav />
    </div>
  );
}

/* 흘러가는 구름 — 히어로의 원본 값(52~76초)을 쓰고, 화면마다 높이·지연만 바꿔
   같은 자리에서 반복돼 보이지 않게 한다 */
const CLOUD_SETS = [
  [['9%', '58s', '0s'], ['54%', '72s', '-26s'], ['81%', '64s', '-11s']],
  [['16%', '67s', '-8s'], ['63%', '55s', '-31s'], ['88%', '75s', '-19s']],
  [['11%', '61s', '-14s'], ['47%', '78s', '-5s'], ['76%', '58s', '-37s']],
];

function Clouds({ seed = 0 }: { seed?: number }) {
  return (
    <div className="lp-sky" aria-hidden="true">
      {CLOUD_SETS[seed % CLOUD_SETS.length].map(([top, dur, delay]) => (
        <div key={top} className="cloud"
          style={{ top, animationDuration: dur, animationDelay: delay }} />
      ))}
    </div>
  );
}

/* 페이지 표시 — 지금 몇 번째 화면인지, 더 있는지.
   폰과 같은 장치인데 데스크톱은 마우스라 눌러서 그 화면으로 보내는 것까지 한다.
   대상은 CSS의 스냅 대상과 같은 선택자로 잡는다(둘이 어긋나면 점 수가 안 맞는다) */
const SNAP_SEL = '.lp > .hero, .lp > section, .lp > .lp-duo';

function SnapNav() {
  const [pages, setPages] = useState<Element[]>([]);
  const [at, setAt] = useState(0);

  useEffect(() => {
    const els = [...document.querySelectorAll(SNAP_SEL)];
    setPages(els);
    const io = new IntersectionObserver(
      (es) => es.forEach((e) => { if (e.isIntersecting) setAt(els.indexOf(e.target)); }),
      { threshold: 0.6 }
    );
    els.forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, []);

  return (
    <nav className="lp-nav" aria-label={`${pages.length}화면 중 ${at + 1}번째`}>
      {pages.map((el, i) => (
        <button key={i} type="button" className={i === at ? 'on' : ''}
          aria-label={`${i + 1}번째 화면으로`}
          onClick={() => el.scrollIntoView({ behavior: 'smooth' })} />
      ))}
      {at < pages.length - 1 && <span className="lp-more" aria-hidden="true">▾</span>}
    </nav>
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
          약점은 퀴즈로, 퀴즈는 코인이 됩니다.<br />
          코인으로 코기에게 먹이를 주세요.
        </p>
        <div className="cta-row">
          {/* 가입·로그인을 한 버튼으로 합쳤다 — 로그인 화면에 회원가입 링크가 있다 */}
          <Link className="btn co" to="/login">로그인 or 회원가입하고 시작</Link>
          <Link className="btn wh" to="/guest">비로그인 체험</Link>
        </div>
      </div>

      <CorgiDevice />
    </header>
  );
}

function ReviewSection() {
  return (
    <section id="review">
      <div className="wrap">
        <span className="badge">CODE REVIEW</span>
        <h2 className="sec-title">머지 버튼을 누르기 전에, 코기가 먼저 읽습니다</h2>
        {/* 줄바꿈은 쉼표·마침표에서 끊는다 — 어절 중간에서 접히면 문장이 잘려 읽힌다 */}
        <p className="sec-lead">
          버그와 성능 저하, 구조 개선, 코드 스타일, 보안 취약점을<br />
          한 번에 살펴보고 급한 것부터 알려드립니다.<br />
          새벽 3시에 올린 커밋도 빠뜨리지 않습니다.
        </p>
        <div className="rev-grid">
          <Reveal className="rev-panel">
            <div className="rev-head"><span>pull_request #42</span><span>review</span></div>
            <div className="rev-line"><b className="sev hi">버그 · 심각</b>user.ts:88 · null 체크 누락</div>
            <div className="rev-line"><b className="sev mid">성능 저하 · 주의</b>render.tsx:51 · 불필요한 리렌더</div>
            <div className="rev-line"><b className="sev low">코드 스타일 · 경미</b>date.ts:12 · 네이밍 컨벤션</div>
            <div className="rev-foot">"null 안전성" 3회 반복 → 약점 승격 · 학습카드로 전환</div>
          </Reveal>
          <Reveal as="ul" className="rev-list" delay={80}>
            <li><b>5종 카테고리</b>리뷰어마다 기준이 달라지지 않게,<br />언제나 같은 다섯 가지로 봅니다.</li>
            <li><b>심각도 3단계 라인 코멘트</b>심각·주의·경미로 나눠<br />해당 줄에 답니다.</li>
            <li><b>"의도한 코드" 버튼</b>일부러 그렇게 짰다면 버튼 한 번으로 끝,<br />그 지적은 약점 통계에서 빠집니다.</li>
            <li><b>결과 내보내기</b>팀 회고에 쓰기 좋게<br />Markdown과 PDF로 바로 뽑아 갑니다.</li>
          </Reveal>
        </div>
      </div>
    </section>
  );
}

function LoopSection() {
  // 단계마다 그 순간의 코기 모습을 붙인다 — 기기(LCD)에서 쓰는 같은 도트다.
  // 문구는 절 단위로 끊는다 — 4열이라 카드 폭이 좁고, 안 끊으면 어절 중간에서 접힌다
  const steps: [string, JSX.Element, JSX.Element, string][] = [
    ['STEP 1', <>리뷰 · 학습</>, <>같은 지적이 세 번 쌓이면 약점이 되고,<br />그 약점으로 학습카드가 만들어집니다.</>, SPR.sad],
    ['STEP 2', <>코인 획득</>, <>퀴즈를 맞히면 코인 20, 틀려도 5.<br />하루 첫 접속엔 출석 10.</>, SPR.gold],
    ['STEP 3', <>코기 돌보기</>, <>밥 주기 10, 산책 15, 청소 5.<br />코인 쓸 곳은 한 마리뿐입니다.</>, SPR.eat],
    ['STEP 4', <>함께 성장</>, <>돌볼 때마다 경험치가 오르고,<br />300을 넘으면 대장 코기가 됩니다.</>, SPR.boss],
  ];
  return (
    <section className="loop" id="learn">
      <div className="wrap">
        <span className="badge">GROWTH LOOP</span>
        <h2 className="sec-title">미루던 공부를, 코기가 조릅니다</h2>
        <p className="sec-lead">
          인강 결제만 하고 안 듣는 사람, 저희 팀에도 있습니다.<br />
          그래서 공부를 시키는 대신 굶는 코기를 보여 주기로 했습니다.
        </p>
        <div className="loop-grid">
          {steps.map(([n, t, d, spr], i) => (
            <Reveal className="l-card" key={n} delay={i * 80}>
              <span className="n">{n}</span><h3>{t}</h3><p>{d}</p>
              <img className="l-spr" src={spr} alt="" />
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}

function CareSection() {
  // 스프라이트는 기기(LCD)에서 쓰는 원본 도트를 그대로 재사용한다 — 눌렀을 때 보게 될 모습이다
  const cares: [string, string, string, JSX.Element, string][] = [
    ['🍖', '먹이주기', '코인 10', <>배부름이 32 오릅니다.<br />사료는 학습카드의 퀴즈를 풀어서.</>, SPR.eat],
    ['🐾', '산책하기', '코인 15', <>행복이 32 오르고<br />배부름이 조금 줍니다.<br />코기가 제일 좋아하는 것.</>, SPR.run1],
    ['🧹', '청소하기', '코인 5', <>청결이 42 오릅니다.<br />안 치우면 코기가 시무룩해집니다.</>, SPR.happy],
  ];
  return (
    <section id="care">
      <div className="wrap">
        <span className="badge">STUDY → COIN → CARE</span>
        <h2 className="sec-title">귀여움에도 근거가 있습니다</h2>
        <p className="sec-lead">
          버튼 세 개는 장식이 아닙니다.<br />
          쓰는 코인이 전부 학습에서 나오기 때문에,<br />
          코기를 굶기지 않으려면 오늘 퀴즈를 풀어야 합니다.
        </p>
        <div className="care-grid">
          {cares.map(([em, t, map, d, spr], i) => (
            <Reveal className="care" key={t} delay={i * 80}>
              <div className="head"><span className="em">{em}</span><h3>{t}</h3></div>
              <span className="map">{map}</span>
              <p>{d}</p>
              <img className="care-spr" src={spr} alt="" />
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
    // 배경·테두리는 landing.css의 .pet-sec으로 옮겼다 — 인라인이면 랜딩에서 못 걷어낸다
    <section id="pet" className="pet-sec">
      <div className="wrap">
        <span className="badge">TAMAGOTCHI</span>
        <h2 className="sec-title">알에서 대장 코기까지</h2>
        <p className="sec-lead">
          레벨이 오르면 몸집과 귀, 장비가 달라집니다.<br />
          매주 월요일 9시 주간 리포트에는 실력 그래프 옆에<br />
          코기 근황이 같이 붙습니다.
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
  // 이름·가격·크레딧·모델은 서버가 원천이다. 랜딩에만 옛 값이 박혀 실제 요금제와 어긋나 있었다
  const [plans, setPlans] = useState(PLAN_FALLBACK);
  useEffect(() => {
    api.getPlans().then((ps) => { if (ps?.length) setPlans(ps); }).catch(() => {});
  }, []);

  return (
    <section id="plan">
      <div className="wrap">
        <span className="badge">SELECT YOUR PLAYER</span>
        <h2 className="sec-title">플랜 선택</h2>
        <p className="sec-lead">
          위로 갈수록 고를 수 있는 모델이 늘고, 하루에 더 많이 씁니다.<br />
          크레딧은 자정에 초기화됩니다.
        </p>
        <div className="chars">
          {plans.map((p, i) => {
            const mine = cur === p.name;        // 현재 이용 중인 플랜
            const added = addedModels(plans, i);
            return (
              <Reveal className={`char ${p.name === 'PRO' ? 'mid' : ''} ${mine ? 'current' : ''}`} key={p.name} delay={i * 80}>
                {mine && <div className="plan-flag">현재 이용 중</div>}
                <div className="pname">{p.name}</div>
                <div className="model">{p.price === 0 ? '무료' : `₩${p.price.toLocaleString()} / 월`}</div>
                <div className="credit">{p.dailyCreditLimit}<small>credits / day · 자정 초기화</small></div>
                {/* 고를 수 있는 모델 — 칩으로 두면 플랜마다 어디까지 열리는지가 한눈에 보인다 */}
                <div className="chips">
                  {i > 0 && <span className="chips-plus">아래 플랜 전부 +</span>}
                  {added.map((m) => <span className="chip" key={m}>{m}</span>)}
                </div>
                <ul><li>{PLAN_NOTE[p.name]}</li></ul>
                {/* 비로그인: 가입 유도 / 로그인: 현재 플랜은 '사용 중', 그 외는 요금제 페이지로 */}
                {!user ? (
                  <Link className={`btn ${p.name === 'PRO' ? '' : 'wh'}`} to="/signup">
                    {p.name === 'FREE' ? '무료로 시작' : `${p.name} 시작`}
                  </Link>
                ) : mine ? (
                  <span className="btn wh" style={{ opacity: .6, cursor: 'default' }}>사용 중</span>
                ) : (
                  <Link className={`btn ${p.name === 'PRO' ? '' : 'wh'}`} to="/app/plan">플랜 변경 →</Link>
                )}
              </Reveal>
            );
          })}
        </div>
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
