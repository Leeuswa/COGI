/*
 * 게임/리텐션 상태의 단일 저장소.
 *  - 코기 스탯(배부름/행복/청결/XP)과 코인 지갑
 *  - streak (퀴즈 "제출" 기준. 정답 여부 무관 — FR-74)
 *  - 일일 크레딧 (플랜별 20/40/70, 자정 초기화 — FR-81/85)
 *
 * 코기 스탯과 코인은 계정별로 DB에 저장한다(GET·PUT /api/users/me/pet).
 * 예전엔 localStorage에만 있어서 계정을 바꿔도 이전 코기가 그대로 남고,
 * 브라우저를 바꾸면 처음부터 시작하는 문제가 있었다.
 * streak과 크레딧은 각각 API-051, API-055가 원천이고 여기선 화면 캐시로만 들고 있다.
 */
import { createContext, useContext, useState, useCallback, useRef, useEffect } from 'react';
import { useAuth } from './AuthContext';
import * as api from '../api/client';

const GameCtx = createContext(null);

const today = () => new Date().toISOString().slice(0, 10);

// 플랜별 일일 크레딧 상한 (FR-85)
const CREDIT_LIMIT = { FREE: 20, PRO: 40, MAX: 70 };

// 코기 캐시는 계정별로 나눈다. 한 키에 몰아 두면 세션 만료 401(client.ts)처럼
// 페이지가 통째로 리로드되는 경로에서 로그아웃 처리가 못 돌아,
// 그 계정 코기가 비로그인 랜딩에 Lv.2로 그대로 남는다
const petKey = (u) => (u ? `cogi-game-${u.userId ?? u.email}` : 'cogi-game');
const cachedUser = () => {
  try { return JSON.parse(localStorage.getItem('cogi-user') || 'null'); } catch { return null; }
};

const DEFAULT = {
  coins: 30, hun: 60, hap: 60, cln: 60, xp: 0,   // 원본 다마고치 초기값 그대로
  // streak은 "연속" 학습일이라 하루 걸리면 0. totalDays는 끊겨도 줄지 않는 누적 학습일이라 별개다
  streak: 0, totalDays: 0, lastSubmitDate: null, submitDays: [], // submitDays: 최근 제출 날짜 목록(달력 점 찍기용)
  lastCheckIn: null,             // 출석 코인 지급일 (하루 1회)
  creditUsed: 0, creditDate: today(),
};

export function GameProvider({ children }) {
  const { user } = useAuth();

  const [S, setS] = useState(() => {
    try {
      // 코기 스탯은 서버에서 받아 덮어쓴다. 여기 캐시는 새로고침 직후 잠깐 보여줄 용도
      const saved = JSON.parse(localStorage.getItem(petKey(cachedUser())) || 'null');
      if (!saved) return DEFAULT;
      // 날짜가 넘어갔으면 크레딧 리셋 (자정 초기화 흉내 — 실제론 서버 배치)
      if (saved.creditDate !== today()) { saved.creditUsed = 0; saved.creditDate = today(); }
      // 하루 이상 제출을 걸렀으면 streak 끊김 (FR-74)
      if (saved.lastSubmitDate) {
        const gap = (new Date(today()).getTime() - new Date(saved.lastSubmitDate).getTime()) / 86400000;
        if (gap > 1) saved.streak = 0;
      }
      return { ...DEFAULT, ...saved };
    } catch { return DEFAULT; }
  });

  const [toast, setToast] = useState(null);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const loaded = useRef(false); // 서버 값을 받기 전에 저장을 쏘면 기본값으로 덮어써 버린다

  // 로그인한 계정의 코기를 불러온다. 계정이 바뀌면 그 계정 코기로 갈아끼운다
  const prevUser = useRef(user);
  useEffect(() => {
    const was = prevUser.current;
    prevUser.current = user;
    if (!user) {
      loaded.current = false;
      // 로그아웃 — 화면에 떠 있는 그 계정 코기를 내린다(캐시는 계정 키에 따로 있다).
      // 비로그인으로 처음 들어온 방문자(was == null)의 체험 코기는 건드리지 않는다
      if (was) setS(DEFAULT);
      return;
    }
    let alive = true;
    api.getPetState().then((pet) => {
      if (!alive || !pet) return;
      setS((prev) => ({
        ...prev,
        coins: pet.coins, hun: pet.fullness, hap: pet.happiness, cln: pet.cleanliness, xp: pet.xp,
        lastCheckIn: pet.lastCheckIn,
      }));
      loaded.current = true;
    }).catch(() => { /* 조회 실패면 캐시된 값으로 계속 */ });

    // streak과 제출 달력도 DB(user_streaks·quiz_submissions)가 원천이다.
    // 예전엔 캐시가 이겨서 DB를 새로 심어도 옛 기록이 그대로 남아 있었다
    api.getRetentionStatus().then((r) => {
      if (!alive || !r) return;
      const days = r.submitDays ?? [];
      setS((prev) => ({
        ...prev,
        streak: r.streak ?? 0,
        totalDays: r.totalDays ?? 0,
        submitDays: days,
        lastSubmitDate: days.length > 0 ? days[days.length - 1] : null, // 오름차순이라 마지막이 최근
      }));
    }).catch(() => { /* 조회 실패면 캐시된 값으로 계속 */ });
    return () => { alive = false; };
  }, [user?.userId ?? user?.email]);

  // 상태 변경 + 저장을 한 번에. 모든 액션이 이 함수만 거친다
  const update = useCallback((patch) => {
    setS((prev) => {
      const next = typeof patch === 'function' ? patch(prev) : { ...prev, ...patch };
      localStorage.setItem(petKey(user), JSON.stringify(next));

      // 코기 스탯만 서버로. 먹이 주기처럼 연달아 눌리는 동작이라 잠깐 모았다 한 번에 보낸다
      if (loaded.current) {
        if (saveTimer.current) clearTimeout(saveTimer.current);
        saveTimer.current = setTimeout(() => {
          api.savePetState({
            coins: next.coins, fullness: next.hun, happiness: next.hap,
            cleanliness: next.cln, xp: next.xp, lastCheckIn: next.lastCheckIn,
          }).catch(() => { /* 저장 실패는 조용히 넘긴다 — 다음 변경 때 다시 보낸다 */ });
        }, 600);
      }
      return next;
    });
  }, [user]); // 저장 키가 계정별이라 user가 바뀌면 새로 만든다

  // 우하단 토스트. 연속 호출되면 마지막 것만 남는다 (그게 자연스러움)
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const notify = useCallback((msg) => {
    setToast(msg);
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 2600);
  }, []);

  const creditLimit = CREDIT_LIMIT[user?.planName] ?? 20;

  // AI 호출류(리뷰/카드생성/퀴즈생성/스킬추천)는 전부 이걸 거친다 (SYS-003)
  // 소진이면 false. 90% 넘어가는 순간 한 번 경고
  // 일일 크레딧 소비 + 90%/100% 도달 알림 (FR-80~81, API-055 규칙)
  const spendCredit = useCallback((n = 1) => {
    let ok = false;
    update((prev) => {
      if (prev.creditUsed + n > creditLimit) return prev; // 402 상황
      ok = true;
      const used = prev.creditUsed + n;
      if (used / creditLimit >= 0.9 && prev.creditUsed / creditLimit < 0.9) {
        notify(`크레딧 90% 사용! 오늘 ${creditLimit - used}개 남았어요`);
      }
      return { ...prev, creditUsed: used };
    });
    if (!ok) notify('오늘 크레딧을 다 썼어요. 자정에 초기화됩니다');
    return ok;
  }, [creditLimit, update, notify]);

  // spendCredit의 반대 — AI 호출이 실패하면 서버는 @Transactional 롤백으로 크레딧을 안 쓰므로
  // 미리 차감했던 로컬 값도 되돌린다 (요청 자체가 안 갔거나 서버 에러로 끝난 경우 전부 해당)
  const refundCredit = useCallback((n = 1) => {
    update((prev) => ({ ...prev, creditUsed: Math.max(0, prev.creditUsed - n) }));
  }, [update]);

  // 퀴즈 제출 → streak 갱신. 정답 여부와 무관하게 "제출"이 기준 (FR-74)
  const recordSubmit = useCallback(() => {
    update((prev) => {
      if (prev.lastSubmitDate === today()) return prev; // 오늘 이미 찍음
      return {
        ...prev,
        streak: prev.streak + 1,
        lastSubmitDate: today(),
        submitDays: [...prev.submitDays.slice(-27), today()], // 최근 4주만 유지
      };
    });
  }, [update]);

  // 매일 출석 보상 — 하루 첫 방문에 코인 +10 (문제 풀이 코인과 별개)
  const checkIn = useCallback(() => {
    update((prev) => {
      if (prev.lastCheckIn === today()) return prev; // 오늘 이미 받음
      setTimeout(() => notify('🎁 출석 완료! +10코인'), 400);
      return { ...prev, coins: prev.coins + 10, lastCheckIn: today() };
    });
  }, [update, notify]);

  const value = {
    S, update, notify, toast,
    creditLimit, spendCredit, refundCredit, recordSubmit, checkIn,
    earnCoins: (n) => update((p) => ({ ...p, coins: p.coins + n })),
    // 서버 집계값으로 크레딧/스트릭 보정 (API-055/051) — 서버가 항상 이긴다
    syncServer: (credit, retention) => update((p) => ({
      ...p,
      creditUsed: credit?.usedCredits ?? p.creditUsed,
      streak: retention?.streak ?? p.streak,
      totalDays: retention?.totalDays ?? p.totalDays,
      submitDays: retention?.submitDays ?? p.submitDays, // 달력 점도 서버 값으로
    })),
  };

  return (
    <GameCtx.Provider value={value}>
      {children}
      {toast && <div className="toast">{toast}</div>}
    </GameCtx.Provider>
  );
}

export const useGame = () => useContext(GameCtx);
