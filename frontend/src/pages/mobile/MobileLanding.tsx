/*
 * 모바일 전용 랜딩 (로그인 전 홈).
 * 데스크톱 랜딩을 CSS로 접는 게 아니라 화면을 따로 짰다. 로그인 후 화면과 같은
 * 앱 문법(.mapp + .mcard)이라 가입 전후로 톤이 끊기지 않는다.
 *
 * 구성은 "한 구간 = 한 화면". 스크롤하면 다음 구간이 화면에 딱 걸린다(scroll-snap).
 * 폰마다 화면 높이가 달라도 보는 그림이 같아야 해서, 구간 높이를 dvh로 잡고
 * 안쪽 내용은 세로 가운데에 모은다. 애매하게 걸쳐 잘리는 자리를 없애는 게 목적.
 *
 * 한 페이지에 카드 둘씩 — 4페이지로 끝난다.
 *   1) 소개 + 코기(바로 만져보게 한다. 알에서 시작)
 *   2) 리뷰 예시 + 성장 루프 · 3) 케어=학습 + 성장 단계 · 4) 플랜 + 마지막 진입
 *
 * 화면이 낮은 폰에서는 둘이 안 들어간다. 그때는 CSS가 .ml-page를 display:contents로 지우고
 * .ml-unit(카드 한 장) 하나가 한 화면이 된다 — 8페이지. 스냅도 스텝 표시도 그 단위를 따른다.
 *
 * 문구는 데스크톱 랜딩 것을 그대로 쓴다(같은 약속을 두 벌로 관리하지 않는다).
 */
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import * as api from "../../api/client";
import { PLAN_FALLBACK, PLAN_FOOTNOTE, PLAN_NOTE, addedModels } from "../../data/plans";
import CorgiDevice from "../../components/CorgiDevice";
import { SPR } from "../../assets/sprites";
import "../../styles/mobile/landing.css";

// 카드 한 장 모드로 넘어가는 지점. landing.css의 같은 값과 짝이다 — 한쪽만 고치면 어긋난다
const ONE_CARD = "(max-width: 640px) and (max-height: 820px)";

const STEPS: [string, string, string][] = [
  ["01", "리뷰 · 학습", "같은 지적이 세 번 쌓이면 약점이 되고, 그 약점으로 학습카드가 만들어집니다."],
  ["02", "코인 획득", "퀴즈를 맞히면 20, 틀려도 5. 하루 첫 접속에는 출석 코인 10."],
  ["03", "코기 돌보기", "밥 주기 10, 산책 15, 청소 5. 코인 쓸 곳은 한 마리뿐입니다."],
  ["04", "함께 성장", "돌볼 때마다 경험치가 오르고, 300을 넘으면 대장 코기가 됩니다."],
];

const CARES: [string, string, string][] = [
  ["🍖", "먹이주기 · 코인 10", "배부름이 32 오릅니다. 밥값은 학습으로 벌어야 나옵니다."],
  ["🐾", "산책하기 · 코인 15", "행복이 32 오르고 배부름이 조금 줍니다. 제일 좋아하는 것."],
  ["🧹", "청소하기 · 코인 5", "청결이 42 오릅니다. 안 치우면 코기가 시무룩해집니다."],
];

const STAGES: [string, string, string][] = [
  [SPR.egg, "알", "퀴즈 하나면 깨어납니다"],
  [SPR.baby1, "Lv.1 아기 코기", "XP 1~49"],
  [SPR.kid1, "Lv.2 어린 코기", "XP 50~149"],
  [SPR.adult1, "Lv.3 성견 코기", "XP 150~299"],
  [SPR.boss, "Lv.4 대장 코기", "XP 300+"],
];


export default function MobileLanding() {
  // 페이지 단위로만 넘어가니 스크롤바가 없다. 지금 몇 쪽인지·더 있는지를 표시로 알린다.
  // 스크롤 위치 계산 대신 "화면을 차지한 페이지"를 직접 본다 — 스크롤 컨테이너가
  // 이 화면이 아니라 앱 셸(.app-body)이라 위치 계산은 셸 구조에 묶인다
  // 요금제는 서버 값이 원천이다. 못 받아오면 시드와 같은 값으로 그린다(빈 화면보다 낫다)
  const [plans, setPlans] = useState(PLAN_FALLBACK);
  useEffect(() => {
    api.getPlans().then((ps) => { if (ps?.length) setPlans(ps); }).catch(() => {});
  }, []);

  const [step, setStep] = useState(0);
  const [total, setTotal] = useState(0);
  useEffect(() => {
    let io;
    // 한 화면이 .ml-page(카드 둘)인지 .ml-unit(카드 하나)인지는 화면 높이에 따라 CSS가 정한다.
    // 여기서 높이를 다시 판단하면 기준이 두 벌이 되므로, 계산된 display를 보고 따라간다
    const bind = () => {
      io?.disconnect();
      const secs = [...document.querySelectorAll(".ml-page")];
      const pages = getComputedStyle(secs[0]).display === "contents"
        ? [...document.querySelectorAll(".ml-unit")]
        : secs;
      setTotal(pages.length);
      io = new IntersectionObserver(
        (es) => es.forEach((e) => { if (e.isIntersecting) setStep(pages.indexOf(e.target)); }),
        { threshold: 0.6 }
      );
      pages.forEach((p) => io.observe(p));
    };
    bind();
    const mq = matchMedia(ONE_CARD);
    mq.addEventListener("change", bind);
    return () => { io?.disconnect(); mq.removeEventListener("change", bind); };
  }, []);

  return (
    <main className="mapp mland">
      {/* 1) 첫 화면 */}
      <section className="ml-page">
        <div className="ml-unit">
        <div className="mcard ml-hero">
          <span className="ml-badge">POCKET CORGI · LV.UP EVERY DAY</span>
          <h1>
            어제 까인 코드리뷰가,<br />
            <span className="hl">오늘 코기 사료가 됩니다</span>
          </h1>
          <p className="mnote">
            같은 실수가 세 번이면 약점으로 기록되고,<br />
            약점은 퀴즈로, 퀴즈는 코인이 됩니다.<br />
            코인으로 코기에게 먹이를 주세요.
          </p>
          <Link className="btn co full" to="/login">로그인 or 회원가입하고 시작</Link>
          <Link className="btn wh full" to="/guest">비로그인 체험</Link>
        </div>
        </div>

        {/* 코기는 첫 화면에서 바로 만져보게 한다. 알에서 시작한다 */}
        <div className="ml-unit">
          <CorgiDevice />
          <p className="mlead">↑ 지금 눌러도 됩니다. 퀴즈 하나면 알이 깨집니다.</p>
        </div>
      </section>

      {/* 2) 리뷰 예시 + 성장 루프 */}
      <section className="ml-page">
        <div className="ml-unit">
        <div className="mcard">
          <div className="mcard-head"><h2>🔍 머지 전에 코기가 먼저 읽습니다</h2></div>
          <div className="ml-rev">
            <div className="ml-rev-head"><span>pull_request #42</span><span>review</span></div>
            <div className="ml-rev-line"><b className="sev hi">버그 · 심각</b>user.ts:88 · null 체크 누락</div>
            <div className="ml-rev-line"><b className="sev mid">성능 · 주의</b>render.tsx:51 · 불필요한 리렌더</div>
            <div className="ml-rev-line"><b className="sev low">스타일 · 경미</b>date.ts:12 · 네이밍 컨벤션</div>
            <div className="ml-rev-foot">"null 안전성" 3회 반복 → 약점 승격 · 학습카드로 전환</div>
          </div>
          <ul className="ml-bullets">
            <li><b>5종 카테고리</b>버그 · 성능 저하 · 구조 개선 · 코드 스타일 · 보안 취약점.</li>
            <li><b>심각도 3단계</b>심각·주의·경미로 나눠 해당 줄에 답니다.</li>
            <li><b>"의도한 코드" 버튼</b>일부러 그렇게 짰다면 약점 통계에서 빠집니다.</li>
            <li><b>결과 내보내기</b>회고에 쓰기 좋게 Markdown·PDF로.</li>
          </ul>
        </div>
        </div>

        <div className="ml-unit">
        <div className="mcard">
          <div className="mcard-head"><h2>🔁 미루던 공부를, 코기가 조릅니다</h2></div>
          <ol className="ml-steps">
            {STEPS.map(([n, t, d]) => (
              <li key={n}><span className="ml-n">{n}</span><div><b>{t}</b><p>{d}</p></div></li>
            ))}
          </ol>
        </div>
        </div>
      </section>

      {/* 3) 케어 = 학습 + 성장 단계 */}
      <section className="ml-page">
        <div className="ml-unit">
        <div className="mcard">
          <div className="mcard-head"><h2>🐶 귀여움에도 근거가 있습니다</h2></div>
          <ul className="ml-cares">
            {CARES.map(([em, t, d]) => (
              <li key={t}><span className="ml-em">{em}</span><div><b>{t}</b><p>{d}</p></div></li>
            ))}
          </ul>
        </div>
        </div>

        {/* 성장 단계 — 가로 스크롤은 안 쓴다. 2열로 쌓고 마지막 한 장이 줄을 채운다 */}
        <div className="ml-unit">
        <div className="mcard">
          <div className="mcard-head"><h2>🥚 알에서 대장 코기까지</h2></div>
          <div className="ml-stages">
            {STAGES.map(([src, t, d]) => (
              <div className="ml-stage" key={t}>
                <img src={src} alt={t} />
                <b>{t}</b><i>{d}</i>
              </div>
            ))}
          </div>
        </div>
        </div>
      </section>

      {/* 4) 플랜 + 마지막 진입 */}
      <section className="ml-page">
        <div className="ml-unit">
        <div className="mcard">
          <div className="mcard-head">
            <h2>💳 플랜</h2>
            <span className="mnote">크레딧은 자정 초기화</span>
          </div>
          <div className="ml-plans">
            {plans.map((p, i) => {
              const added = addedModels(plans, i);
              return (
                <div className={`ml-plan${p.name === "PRO" ? " mid" : ""}`} key={p.name}>
                  <div className="ml-plan-top">
                    <b>{p.name}</b>
                    <span className="ml-cred">{p.dailyCreditLimit}<i>크레딧/일</i></span>
                    <span className="ml-price">
                      {p.price === 0 ? "무료" : `₩${p.price.toLocaleString()}`}
                      {p.price > 0 && <i>/월</i>}
                    </span>
                  </div>
                  <div className="ml-chips">
                    {i > 0 && <span className="ml-chips-plus">아래 플랜 전부 +</span>}
                    {added.map((m) => <span className="ml-chip" key={m}>{m}</span>)}
                  </div>
                  <p>{PLAN_NOTE[p.name]}</p>
                </div>
              );
            })}
          </div>
          <p className="mnote">{PLAN_FOOTNOTE}</p>
        </div>
        </div>

        {/* 마지막 진입 — 푸터가 앱 셸에서 감춰지므로 팀 표기도 여기서 받는다 */}
        <div className="ml-unit">
        <div className="mcard ml-end">
          <img className="ml-end-corgi" src={SPR.boss} alt="" />
          <h2>오늘 미션을 끝내면,<br /><span className="hl">코기가 꼬리를 흔듭니다</span></h2>
          <p>가입 1분. 다음 PR부터 코기가 함께 뜁니다.</p>
          <Link className="btn co full" to="/signup">지금 시작하기</Link>
          <Link className="ml-end-alt" to="/guest">가입 전에 먼저 체험해 볼래요</Link>
          <p className="ml-team">Team Fable · 상연 · 이수환 · 홍성찬 · 유지환</p>
        </div>
        </div>
      </section>

      {/* 페이지 표시 — 지금 몇 번째인지와 아래로 더 있다는 신호.
          마지막 페이지에서는 화살표를 숨긴다 */}
      <nav className="ml-steps-nav" aria-label={`${total}쪽 중 ${step + 1}쪽`}>
        {Array.from({ length: total }, (_, i) => (
          <i key={i} className={i === step ? "on" : ""} />
        ))}
        {step < total - 1 && <span className="ml-more" aria-hidden="true">▾</span>}
      </nav>
    </main>
  );
}
