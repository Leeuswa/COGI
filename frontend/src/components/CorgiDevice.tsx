/*
 * 포켓 코기 기기 — 원본 HTML의 다마고치를 그대로 리액트로 옮긴 것.
 * 상태(coins/hun/hap/cln/xp)는 GameContext가 들고 있어서
 * 랜딩과 대시보드 어디서 키우든 같은 코기가 이어진다.
 *
 * 스탯 하락/프레임 루프 같은 타이밍 값(380ms, 4초, 감소폭)은
 * 원본 스크립트 수치를 1도 안 바꿈. 손대면 게임 밸런스 무너지니 주의.
 */
import { useEffect, useRef, useState } from 'react';
import { SPR } from '../assets/sprites';
import { useGame } from '../context/GameContext';

// 성장 단계. XP 구간과 스프라이트 높이는 원본 그대로
const STAGES = [
  { name: '알',             idle: [SPR.egg, SPR.egg],       h: 56 },
  { name: 'Lv.1 아기 코기', idle: [SPR.baby1, SPR.baby2],   h: 52 },
  { name: 'Lv.2 어린 코기', idle: [SPR.kid1, SPR.kid2],     h: 72 },
  { name: 'Lv.3 성견 코기', idle: [SPR.adult1, SPR.adult2], h: 88 },
  { name: 'Lv.4 대장 코기', idle: [SPR.boss, SPR.boss],     h: 90 },
];

const clamp = (v) => Math.max(0, Math.min(100, v));
const stageOf = (xp) => (xp <= 0 ? 0 : xp >= 300 ? 4 : xp >= 150 ? 3 : xp >= 50 ? 2 : 1);

export default function CorgiDevice() {
  const { S, update } = useGame();
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const [frame, setFrame] = useState(0);
  const [action, setAction] = useState('idle'); // idle | eat | walk
  const [bubble, setBubble] = useState('');
  const [flash, setFlash] = useState(null);     // 레벨업 오버레이 텍스트
  const [hatching, setHatching] = useState(false);
  const [coinPulse] = useState(false);

  const lcdRef = useRef(null);
  const actT = useRef(0);       // 액션(eat/walk) 남은 프레임 수
  const poops = useRef([]);     // 응가 DOM들
  const sayT = useRef(null);

  const st = stageOf(S.xp);
  const stage = STAGES[st];

  const say = (t, ms = 1800) => {
    setBubble(t);
    clearTimeout(sayT.current);
    sayT.current = setTimeout(() => setBubble(''), ms);
  };

  /* ── 이펙트: 반짝이/하트/응가는 원본처럼 그냥 DOM으로 뿌린다 ── */
  const spawn = (cls, n, yBase) => {
    const lcd = lcdRef.current;
    if (!lcd) return;
    for (let i = 0; i < n; i++) {
      const el = document.createElement('div');
      el.className = cls;
      el.style.left = 12 + Math.random() * 76 + '%';
      el.style.bottom = yBase + Math.random() * 40 + 'px';
      lcd.appendChild(el);
      setTimeout(() => el.remove(), 1700);
    }
  };
  const addPoop = () => {
    if (poops.current.length >= 3 || !lcdRef.current) return;
    const el = document.createElement('div');
    el.className = 'poop';
    el.style.left = 12 + Math.random() * 70 + '%';
    lcdRef.current.appendChild(el);
    poops.current.push(el);
  };

  /* ── XP 획득 + 레벨업/부화 연출 ── */
  const gainXp = (n) => {
    update((prev) => {
      const before = stageOf(prev.xp);
      const after = stageOf(prev.xp + n);
      if (after > before) {
        if (before === 0) {
          setHatching(true);
          spawn('spark', 6, 50);
          say('알이 깨졌어요! 멍!');
          setTimeout(() => setHatching(false), reduced ? 0 : 1500);
        } else {
          setFlash(STAGES[after].name);
          spawn('spark', 6, 60);
          setTimeout(() => setFlash(null), 1900);
          say('멍멍! 나 컸어!');
        }
      }
      return { ...prev, xp: prev.xp + n };
    });
  };

  /* ── 프레임 루프 (380ms) ── */
  useEffect(() => {
    if (reduced) return;
    const t = setInterval(() => {
      setFrame((f) => f + 1);
      if (actT.current > 0 && --actT.current === 0) setAction('idle');
    }, 380);
    return () => clearInterval(t);
  }, [reduced]);

  /* ── 스탯 자연 감소 (4초) ── */
  useEffect(() => {
    const t = setInterval(() => {
      update((prev) => {
        if (stageOf(prev.xp) === 0) return prev; // 알은 배 안 고픔
        const next = {
          ...prev,
          hun: clamp(prev.hun - 1.5),
          hap: clamp(prev.hap - 1),
          cln: clamp(prev.cln - 0.7),
        };
        if (next.cln < 45 && Math.random() < 0.25) { addPoop(); next.cln = clamp(next.cln - 6); }
        return next;
      });
    }, 4000);
    return () => clearInterval(t);
  }, [update]);

  /* ── 배고픔/심심함 투덜거림 (스탯 감소 주기에 맞춤) ── */
  useEffect(() => {
    if (st === 0) return;
    const m = mood();
    if (m === 'sad' && frame % 8 === 0) {
      if (S.hun < 25) say('배고파... 🍖');
      else if (S.hap < 25) say('심심해... 산책 가자');
      else say('집이 지저분해...');
    }
    if (m === 'exhausted' && frame % 10 === 0) say('지쳐서 잠들었어요...');
  }, [frame]); // eslint-disable-line react-hooks/exhaustive-deps

  /* ── 첫 인사 ── */
  useEffect(() => {
    const t = setTimeout(() => say(st === 0 ? '학습카드를 풀어서 알을 깨워줘!' : '멍! 학습카드로 나를 키워줘!'), 900);
    return () => clearTimeout(t);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  /* ── 기분 판정 ── */
  function mood() {
    if (st === 0) return 'egg';
    if (S.hun < 20 && S.hap < 20 && S.cln < 20) return 'exhausted';
    if (S.hun < 25 || S.hap < 25 || S.cln < 25) return 'sad';
    if (S.hun > 70 && S.hap > 70 && S.cln > 70) return 'happy';
    return 'normal';
  }

  /* ── 스프라이트/모션 결정 ── */
  const m = mood();
  let src = stage.idle[frame % 2];
  let h = stage.h;
  let slotCls = 'pet-slot';
  if (hatching) { src = SPR.hatch; }
  else if (action === 'walk') { src = frame % 2 ? SPR.run1 : SPR.run2; slotCls += ' hop'; }
  else if (action === 'eat') { src = SPR.eat; slotCls += ' bob'; }
  else if (m === 'egg') { src = SPR.egg; if (!reduced) slotCls += ' wobble'; }
  else if (m === 'exhausted') { src = SPR.bed; h += 8; }
  else if (m === 'sad') { src = SPR.sad; }
  else if (m === 'happy' && frame % 6 < 2) { src = SPR.happy; slotCls += ' bob'; }
  else if (!reduced) slotCls += ' bob';

  /* ── 케어 액션 3종. 비용/회복량 원본 그대로 ── */
  const feed = () => {
    if (S.coins < 10) return;
    update((p) => ({ ...p, coins: p.coins - 10, hun: clamp(p.hun + 32) }));
    setAction('eat'); actT.current = 8;
    say('냠냠냠!'); gainXp(3);
  };
  const walk = () => {
    if (S.coins < 15) return;
    update((p) => ({ ...p, coins: p.coins - 15, hap: clamp(p.hap + 32), hun: clamp(p.hun - 5) }));
    setAction('walk'); actT.current = 14;
    spawn('heart', 3, 110);
    say('산책 최고! 🐾'); gainXp(4);
  };
  const clean = () => {
    if (S.coins < 5) return;
    update((p) => ({ ...p, coins: p.coins - 5, cln: clamp(p.cln + 42) }));
    spawn('spark', 5, 50);
    poops.current.forEach((p) => p.remove());
    poops.current = [];
    say('반짝반짝!'); gainXp(2);
  };

  // XP 게이지: 현재 단계 구간 내 진행률
  const base = [0, 1, 50, 150, 300][st];
  const nextXp = [1, 50, 150, 300, 300][st];
  const xpPct = st >= 4 ? 100 : Math.max(0, Math.round(((S.xp - base) / (nextXp - base)) * 100));

  return (
    <div className="device">
      <div className="brand"><span>COGI · POCKET CORGI</span><i /></div>

      <div className="screen-frame">
        <div className={`lcd${action === 'walk' ? ' walking' : ''}`} ref={lcdRef}>
          <div className="c1" /><div className="c2" />
          <div className="tuft t1" /><div className="tuft t2" /><div className="tuft t3" />
          <div className={slotCls}>
            <img src={src} alt="코기" style={{ height: h + 'px' }} />
          </div>
          <div className={`bubble${bubble ? ' on' : ''}`}>{bubble}</div>
          <div className={`lvl-flash${flash ? ' on' : ''}`}>
            {flash && <><img src={SPR.gold} alt="" /><span>LEVEL UP!<br />{flash}</span></>}
          </div>
        </div>
      </div>

      <div className="meta-row">
        <span className="coin"><i /><b className={coinPulse ? 'pulse' : ''}>{S.coins}</b></span>
        {/* 개발/테스트용 — 코인 +10. 배포 시 제거 예정 */}
        <button
          type="button"
          onClick={() => update((p) => ({ ...p, coins: p.coins + 10 }))}
          style={{ marginLeft: 6, padding: '1px 6px', fontSize: 10, lineHeight: 1.4, cursor: 'pointer' }}
        >
          코인 추가(테스트용)
        </button>
        <span>{stage.name}</span>
      </div>

      <div className="stats">
        <Stat cls="hun" label="배부름" v={S.hun} />
        <Stat cls="hap" label="행복" v={S.hap} />
        <Stat cls="cln" label="청결" v={S.cln} />
        <div className="stat xp">
          <div className="lb"><span>경험치</span><span>{S.xp}</span></div>
          <div className="bar"><i style={{ width: xpPct + '%' }} /></div>
        </div>
      </div>

      <div className="pad three">
        <button onClick={feed} disabled={S.coins < 10}>
          <span className="ico">🍖</span>먹이주기<small>-10</small>
        </button>
        <button onClick={walk} disabled={S.coins < 15}>
          <span className="ico">🐾</span>산책하기<small>-15</small>
        </button>
        <button onClick={clean} disabled={S.coins < 5}>
          <span className="ico">🧹</span>청소하기<small>-5</small>
        </button>
      </div>
    </div>
  );
}

// 스탯 바 한 줄. 4개 반복이라 뽑아둠
function Stat({ cls, label, v }) {
  return (
    <div className={`stat ${cls}`}>
      <div className="lb"><span>{label}</span><span>{Math.round(v)}</span></div>
      <div className="bar"><i style={{ width: v + '%' }} /></div>
    </div>
  );
}
