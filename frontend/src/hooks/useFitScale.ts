/*
 * 대시보드(로그인 홈)를 어떤 해상도에서도 한 화면에 담는다.
 * 패널 크기가 고정 px라 세로 여유가 적은 화면에선 내용이 뷰포트를 넘어 스크롤이 생겼다.
 * box(= nav와 footer 사이 가용 영역)에 딱 맞도록 content를 통째로 transform:scale 한다.
 *   넘칠 때만 줄이고(≤1) 키우진 않는다 — 큰 화면은 원래 배율(1.0) 그대로다.
 * offsetHeight는 transform의 영향을 받지 않아 항상 원래 높이라, 재고 줄이는 되먹임 고리가 없다.
 */
import { useLayoutEffect, useRef } from "react";

export default function useFitScale() {
  const boxRef = useRef<HTMLElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    const box = boxRef.current;
    const content = contentRef.current;
    if (!box || !content) return;

    const fit = () => {
      const top = box.getBoundingClientRect().top; // sticky 헤더 아래 시작점
      const footH = document.querySelector<HTMLElement>("footer.ft")?.offsetHeight ?? 0;
      const availH = window.innerHeight - top - footH - 4; // 4: 바닥 여백
      box.style.height = `${Math.max(availH, 0)}px`;
      // box는 nav~footer 전체를 채우고, 내용은 .dash의 상하 패딩 안에서 중앙 정렬된다.
      // 그래서 위(nav 쪽)·아래(footer 쪽) 여백이 같아진다. 스케일도 패딩을 뺀 높이에 맞춘다.
      const cs = getComputedStyle(box);
      const padY = parseFloat(cs.paddingTop) + parseFloat(cs.paddingBottom);
      const natH = content.offsetHeight;
      if (!natH || availH - padY <= 0) return;
      content.style.transform = `scale(${Math.min(1, (availH - padY) / natH)})`;
    };

    fit();
    window.addEventListener("resize", fit);
    // 폰트 로드·비동기 데이터로 content 높이가 뒤늦게 바뀌는 것까지 반영
    const ro = new ResizeObserver(fit);
    ro.observe(content);
    return () => {
      window.removeEventListener("resize", fit);
      ro.disconnect();
    };
  }, []);

  return { boxRef, contentRef };
}
