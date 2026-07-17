// 초 단위 실시간 카운트다운. start(초)로 시작 → 매초 1씩 감소 → 0에서 멈춤.
// 컴포넌트가 사라지면 타이머 자동 정리(메모리 누수 방지).
import { useState, useRef, useEffect } from 'react';

export function useCountdown(): [number, (seconds: number) => void] {
  const [remaining, setRemaining] = useState(0);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  const start = (seconds: number) => {
    if (timer.current) clearInterval(timer.current);
    setRemaining(seconds);
    timer.current = setInterval(() => {
      setRemaining((r) => {
        if (r <= 1) {
          if (timer.current) clearInterval(timer.current);
          return 0;
        }
        return r - 1;
      });
    }, 1000);
  };

  useEffect(() => () => { if (timer.current) clearInterval(timer.current); }, []);

  return [remaining, start];
}
