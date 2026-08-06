/*
 * 화면 폭으로 모바일 여부를 판단한다.
 * CSS로 데스크톱 화면을 접는 대신, 모바일에서는 아예 다른 컴포넌트를 그리려고 만들었다.
 * 640px 기준은 responsive.css의 모바일 화면 브레이크포인트와 같다.
 * 태블릿(641~1024)은 여기서 false다 — 화면은 데스크톱 것을 그리고,
 * 껍데기(상단 헤더·하단 탭바)만 responsive.css가 ≤1024에서 모바일로 바꾼다.
 */
import { useEffect, useState } from "react";

export default function useIsMobile(query = "(max-width: 640px)") {
  const [is, setIs] = useState(() => window.matchMedia(query).matches);

  useEffect(() => {
    const mq = window.matchMedia(query);
    const on = (e: MediaQueryListEvent) => setIs(e.matches);
    mq.addEventListener("change", on);
    setIs(mq.matches); // 마운트 시점과 어긋났을 수 있으니 한 번 맞춘다
    return () => mq.removeEventListener("change", on);
  }, [query]);

  return is;
}
