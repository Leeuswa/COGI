/*
 * 게임/리텐션 상태의 단일 저장소.
 *  - 코기 스탯(배부름/행복/청결/XP)과 코인 지갑
 *  - streak (퀴즈 "제출" 기준. 정답 여부 무관 — FR-74)
 *  - 일일 크레딧 (플랜별 20/40/70, 자정 초기화 — FR-81/85)
 *
 * 전부 localStorage('cogi-game')에 저장. 백엔드 붙으면 streak/크레딧은
 * API-051, API-055가 원천이 되고 여기는 화면 캐시 역할만 남는다.
 * 코인/스탯은 프론트 게임 요소라 당분간 로컬 유지가 맞다고 보고 있음.
 */
import { createContext, useContext, useState, useCallback, useRef } from 'react';
import { useAuth } from './AuthContext';

const GameCtx = createContext(null);

const today = () => new Date().toISOString().slice(0, 10);

// 플랜별 일일 크레딧 상한 (FR-85)
const CREDIT_LIMIT = { FREE: 20, PRO: 40, MAX: 70 };

const DEFAULT = {
  coins: 30, hun: 60, hap: 60, cln: 60, xp: 0,   // 원본 다마고치 초기값 그대로
  streak: 0, lastSubmitDate: null, submitDays: [], // submitDays: 최근 제출 날짜 목록(달력 점 찍기용)
  lastCheckIn: null,             // 출석 코인 지급일 (하루 1회)
  creditUsed: 0, creditDate: today(),
};

export function GameProvider({ children }) {
  const { user } = useAuth();

  const [S, setS] = useState(() => {
    try {
      const saved = JSON.parse(localStorage.getItem('cogi-game') || 'null');
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

  // 상태 변경 + 저장을 한 번에. 모든 액션이 이 함수만 거친다
  const update = useCallback((patch) => {
    setS((prev) => {
      const next = typeof patch === 'function' ? patch(prev) : { ...prev, ...patch };
      localStorage.setItem('cogi-game', JSON.stringify(next));
      return next;
    });
  }, []);

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
    creditLimit, spendCredit, recordSubmit, checkIn,
    earnCoins: (n) => update((p) => ({ ...p, coins: p.coins + n })),
    // 서버 집계값으로 크레딧/스트릭 보정 (API-055/051) — 서버가 항상 이긴다
    syncServer: (credit, retention) => update((p) => ({
      ...p,
      creditUsed: credit?.used ?? p.creditUsed,
      streak: retention?.streak ?? p.streak,
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
