/*
 * 미리보기 도크 v2 — "피그마처럼" 편집 (FR-47~49 확장)
 *
 * 캔버스: 호버 하이라이트 → 클릭 선택(W×H 배지) → 드래그 이동 / 우하단 모서리 드래그 리사이즈
 *         키보드: 방향키 1px(Shift=10px) 이동 · Delete 삭제 · Ctrl+D 복제
 *         줌(50~150%) · 실행취소/다시실행(Ctrl+Z / Ctrl+Shift+Z) · 요소 추가 5종
 * 패널:   텍스트 / 배치 / 스타일 / 효과 4섹션 — 폰트·회전·그라데이션·필터(블러/밝기/대비/채도/흑백)까지
 *
 * 동기화 원리(변함없음): 모든 편집은 iframe DOM에 인라인 적용 → body 직렬화 → 코드로.
 * 그래서 "패널에 있는 것"만이 아니라 코드로 표현되는 무엇이든 결과물에 남는다.
 * 한계선: iframe 정적 렌더(HTML/CSS/JS). React 등 빌드 필요 코드는 리뷰만.
 */
import { useEffect, useMemo, useRef, useState } from "react";

/* ── iframe 편집 엔진: 선택·드래그·리사이즈·키보드·명령 수신·직렬화 ── */
const ENGINE = `<script id="__cogi_engine">
(() => {
  let sel = null;
  const toHex = (rgb) => {
    const m = rgb.match(/\\d+/g); if (!m) return '#000000';
    return '#' + m.slice(0, 3).map((n) => (+n).toString(16).padStart(2, '0')).join('');
  };

  const composeFx = () => {
    const f = JSON.parse(sel.dataset.fx || '{}');
    const parts = [];
    if (f.blur) parts.push('blur(' + f.blur + 'px)');
    if (f.bright != null && f.bright !== 100) parts.push('brightness(' + f.bright + '%)');
    if (f.contrast != null && f.contrast !== 100) parts.push('contrast(' + f.contrast + '%)');
    if (f.sat != null && f.sat !== 100) parts.push('saturate(' + f.sat + '%)');
    if (f.gray) parts.push('grayscale(' + f.gray + '%)');
    sel.style.filter = parts.join(' ');
    sel.style.transform = f.rot ? 'rotate(' + f.rot + 'deg)' : '';
  };

  /* ── 선택 오버레이: 8방향 리사이즈 핸들 + 상단 회전 핸들 (피그마식) ── */
  const ov = document.createElement('div');
  ov.id = '__cogi_ov';
  ov.style.cssText = 'position:absolute;pointer-events:none;z-index:99999;display:none;outline:2px solid #ff6b57;outline-offset:0';
  const HANDLES = ['nw','n','ne','e','se','s','sw','w'];
  HANDLES.forEach((h) => {
    const d = document.createElement('div');
    d.dataset.h = h;
    d.style.cssText = 'position:absolute;width:9px;height:9px;background:#fff;border:2px solid #1b2a4a;pointer-events:auto;cursor:' + h + '-resize;';
    ov.appendChild(d);
  });
  const rot = document.createElement('div'); // 회전 핸들 (상단 중앙 위)
  rot.dataset.h = 'rot';
  rot.style.cssText = 'position:absolute;width:14px;height:14px;border-radius:50%;background:#ffd23f;border:2px solid #1b2a4a;pointer-events:auto;cursor:grab;left:50%;top:-26px;transform:translateX(-50%)';
  ov.appendChild(rot);
  const badge = document.createElement('div'); // W×H 배지 (피그마식, 선택 박스 아래 중앙)
  badge.style.cssText = 'position:absolute;left:50%;bottom:-26px;transform:translateX(-50%);background:#1b2a4a;color:#fff;font:700 10px/1 monospace;padding:3px 7px;border:2px solid #1b2a4a;white-space:nowrap';
  ov.appendChild(badge);
  document.addEventListener('DOMContentLoaded', () => document.body.appendChild(ov));

  const layout = () => { // 오버레이를 선택 요소 위치에 맞춘다 (회전 각도 포함)
    if (!sel) { ov.style.display = 'none'; return; }
    const r = sel.getBoundingClientRect();
    const f = JSON.parse(sel.dataset.fx || '{}');
    const w = sel.offsetWidth, h = sel.offsetHeight;
    ov.style.display = 'block';
    ov.style.left = (r.left + r.width / 2 - w / 2 + scrollX) + 'px';
    ov.style.top = (r.top + r.height / 2 - h / 2 + scrollY) + 'px';
    ov.style.width = w + 'px';
    ov.style.height = h + 'px';
    ov.style.transform = f.rot ? 'rotate(' + f.rot + 'deg)' : '';
    const dimLabel = Math.round(w) + ' \u00d7 ' + Math.round(h);
    if (badge.textContent !== dimLabel) badge.textContent = dimLabel; // 같은 값 재설정 금지 — MutationObserver 무한 루프 방지
    const pos = { nw:[0,0], n:[.5,0], ne:[1,0], e:[1,.5], se:[1,1], s:[.5,1], sw:[0,1], w:[0,.5] };
    ov.querySelectorAll('[data-h]').forEach((d) => {
      const p = pos[d.dataset.h]; if (!p) return;
      d.style.left = 'calc(' + (p[0] * 100) + '% - 5px)';
      d.style.top = 'calc(' + (p[1] * 100) + '% - 5px)';
    });
  };
  new MutationObserver(layout).observe(document.documentElement, { attributes: true, childList: true, subtree: true });
  addEventListener('scroll', layout, true);

  const dims = () => {
    if (!sel) return;
    parent.postMessage({ cogi: 'dim', w: sel.offsetWidth, h: sel.offsetHeight }, '*');
  };
  const pick = (el) => {
    sel = el;
    const cs = getComputedStyle(sel);
    parent.postMessage({ cogi: 'pick',
      label: sel.tagName.toLowerCase() + (sel.id ? '#' + sel.id : ''),
      text: sel.children.length === 0 ? sel.textContent.trim() : null,
      style: {
        color: toHex(cs.color), background: toHex(cs.backgroundColor),
        fontSize: parseInt(cs.fontSize), fontWeight: cs.fontWeight, fontFamily: cs.fontFamily,
        align: cs.textAlign, letterSpacing: parseFloat(cs.letterSpacing) || 0,
        padding: parseInt(cs.paddingTop) || 0, margin: parseInt(cs.marginTop) || 0,
        radius: parseInt(cs.borderRadius) || 0, opacity: Math.round(parseFloat(cs.opacity) * 100),
        borderW: parseInt(cs.borderTopWidth) || 0, borderC: toHex(cs.borderTopColor),
      } }, '*');
    layout(); dims();
  };
  // 인라인 style 을 최소 형태로 정리 — 브라우저가 자동으로 채운 longhand(예: border-width/style/color/image)
  // 와 기본값을 걷어내, 한 번 편집해도 코드가 길어지지 않게 한다 (#4)
  const DEFAULTS = {
    'border-image': 'none', 'border-color': 'currentcolor', 'border-style': 'none', 'border-width': 'medium',
    'border-top-style': 'none', 'border-right-style': 'none', 'border-bottom-style': 'none', 'border-left-style': 'none',
    position: 'static', 'text-align': 'start', 'font-style': 'normal', 'text-decoration': 'none solid rgb(0, 0, 0)',
    left: '0px', top: '0px', 'margin-top': '0px', 'margin-left': '0px', filter: 'none', transform: 'none', opacity: '1',
  };
  const tidy = (el) => {
    const st = el.getAttribute('style'); if (!st) return;
    const keep = st.split(';').map((r) => r.trim()).filter(Boolean).filter((r) => {
      const [k, v] = r.split(':').map((x) => x.trim().toLowerCase());
      if (!v) return false;
      if (DEFAULTS[k] !== undefined && v === DEFAULTS[k]) return false; // 기본값 제거
      if (k.startsWith('border') && (v === '0px' || v === 'none')) return false;
      return true;
    });
    // relative 인데 좌표 이동이 없으면 position 도 군더더기 → 제거
    const hasOffset = keep.some((r) => /^(left|top)\s*:/.test(r));
    const finalKeep = keep.filter((r) => !(/^position\s*:\s*relative/.test(r) && !hasOffset));
    finalKeep.length ? el.setAttribute('style', finalKeep.join('; ')) : el.removeAttribute('style');
  };
  const emit = () => {
    const clone = document.body.cloneNode(true);
    const o = clone.querySelector('#__cogi_ov'); if (o) o.remove(); // 편집 UI 는 코드에 안 남긴다
    // 편집 엔진 스크립트도 코드에 안 남긴다 — 과거에 섞여 들어간 사본까지 전부 청소
    clone.querySelectorAll('script').forEach((sc) => {
      if (sc.id === '__cogi_engine' || sc.textContent.includes('__cogi_ov')) sc.remove();
    });
    clone.querySelectorAll('[style]').forEach(tidy);
    const pretty = clone.innerHTML.trim().replace(/></g, '>\\n<'); // 태그마다 개행
    parent.postMessage({ cogi: 'code', code: pretty }, '*');
  };

  /* 호버 미리 표시 */
  let hoverEl = null;
  document.addEventListener('mouseover', (e) => {
    const t = e.target;
    if (t === sel || t.closest('#__cogi_ov') || t === document.body || t === document.documentElement) return;
    if (hoverEl) hoverEl.style.outline = '';
    hoverEl = t; t.style.outline = '2px dashed #7ba7e0';
  });
  document.addEventListener('mouseout', (e) => { if (e.target === hoverEl) { hoverEl.style.outline = ''; hoverEl = null; } });

  /* ── 드래그: 이동 / 핸들 리사이즈 / 회전 ── */
  let drag = null;
  document.addEventListener('mousedown', (e) => {
    window.focus(); // preventDefault 때문에 자동 포커스가 막히므로 직접 포커스 → 키보드 입력이 iframe 으로

    const hd = e.target.closest('[data-h]');
    if (hd && sel) { // 핸들 잡음
      e.preventDefault();
      const r = sel.getBoundingClientRect();
      if (hd.dataset.h === 'rot') {
        const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
        const f = JSON.parse(sel.dataset.fx || '{}');
        drag = { mode: 'rot', cx, cy, base: f.rot || 0,
                 start: Math.atan2(e.clientY - cy, e.clientX - cx) * 180 / Math.PI };
      } else {
        drag = { mode: 'resize', h: hd.dataset.h, x: e.clientX, y: e.clientY,
                 w: sel.offsetWidth, hh: sel.offsetHeight };
      }
      return;
    }
    const t = e.target;
    if (t === document.body || t === document.documentElement || t.closest('#__cogi_ov')) return;
    e.preventDefault();
    if (hoverEl) { hoverEl.style.outline = ''; hoverEl = null; }
    if (t !== sel) pick(t);
    const st = sel.style;
    st.position = st.position || 'relative';
    drag = { mode: 'move', x: e.clientX, y: e.clientY, left: parseInt(st.left) || 0, top: parseInt(st.top) || 0 };
  }, true);

  document.addEventListener('mousemove', (e) => {
    if (!drag || !sel) return;
    if (drag.mode === 'move') {
      sel.style.left = drag.left + (e.clientX - drag.x) + 'px';
      sel.style.top = drag.top + (e.clientY - drag.y) + 'px';
    } else if (drag.mode === 'resize') {
      const dx = e.clientX - drag.x, dy = e.clientY - drag.y, h = drag.h;
      if (h.includes('e')) sel.style.width = Math.max(10, drag.w + dx) + 'px';
      if (h.includes('w')) sel.style.width = Math.max(10, drag.w - dx) + 'px';
      if (h.includes('s')) sel.style.height = Math.max(10, drag.hh + dy) + 'px';
      if (h.includes('n')) sel.style.height = Math.max(10, drag.hh - dy) + 'px';
      dims();
    } else if (drag.mode === 'rot') {
      const now = Math.atan2(e.clientY - drag.cy, e.clientX - drag.cx) * 180 / Math.PI;
      let deg = Math.round(drag.base + now - drag.start);
      if (e.shiftKey) deg = Math.round(deg / 15) * 15; // Shift = 15도 스냅
      const f = JSON.parse(sel.dataset.fx || '{}'); f.rot = deg; sel.dataset.fx = JSON.stringify(f);
      composeFx(); parent.postMessage({ cogi: 'rot', deg }, '*');
    }
    layout();
  });
  document.addEventListener('mouseup', () => { if (drag) { drag = null; emit(); } });

  /* 키보드 */
  document.addEventListener('keydown', (e) => {
    if (!sel) return;
    const step = e.shiftKey ? 10 : 1;
    const mv = { ArrowUp: [0, -step], ArrowDown: [0, step], ArrowLeft: [-step, 0], ArrowRight: [step, 0] }[e.key];
    if (mv) {
      e.preventDefault();
      sel.style.position = sel.style.position || 'relative';
      sel.style.left = ((parseInt(sel.style.left) || 0) + mv[0]) + 'px';
      sel.style.top = ((parseInt(sel.style.top) || 0) + mv[1]) + 'px';
      layout(); emit();
    } else if (e.key === 'Delete' || e.key === 'Backspace') {
      e.preventDefault(); const dead = sel; sel = null; layout(); dead.remove();
      parent.postMessage({ cogi: 'clear' }, '*'); emit();
    } else if ((e.ctrlKey || e.metaKey) && (e.key.toLowerCase() === 'd' || e.code === 'KeyD')) {
      e.preventDefault(); const copy = sel.cloneNode(true);
      sel.after(copy); pick(copy); emit();
    }
  });


  /* 새 요소 — 텍스트는 inline-block: 상자가 글 길이에 딱 맞는다 (요구 #7) */
  const ADD = {
    text: '<p style="display:inline-block;font-size:16px;color:#1b2a4a">새 텍스트를 입력하세요</p>',
    button: '<button style="padding:12px 24px;background:#ff6b57;color:#fff;border:3px solid #1b2a4a;font-size:14px">버튼</button>',
    box: '<div style="width:140px;height:90px;background:#cfe8ff;border:3px solid #1b2a4a"></div>',
    image: '<img alt="이미지 자리" width="140" height="90" style="background:#e8e3d7;border:3px solid #1b2a4a;object-fit:cover" src="data:image/gif;base64,R0lGODlhAQABAIAAAMLCwgAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw==">',
    divider: '<hr style="border:none;border-top:3px solid #1b2a4a;margin:16px 0">',
    input: '<input placeholder="입력하세요" style="padding:10px 14px;border:3px solid #1b2a4a;font-size:14px;width:220px">',
    badge: '<span style="display:inline-block;padding:4px 12px;background:#ffd23f;border:2px solid #1b2a4a;font-size:12px;font-weight:700">NEW</span>',
    card: '<div style="width:240px;padding:18px;background:#fff;border:3px solid #1b2a4a;box-shadow:6px 6px 0 #1b2a4a"><b style="font-size:16px">카드 제목</b><p style="font-size:13px;color:#5a6a85;margin-top:8px">설명 텍스트를 입력하세요.</p></div>',
  };

  addEventListener('message', (e) => {
    const d = e.data || {};
    if (d.cogi === 'add') {
      const wrap = document.createElement('div'); wrap.innerHTML = ADD[d.kind];
      const node = wrap.firstChild;
      (sel ? sel.after(node) : document.body.appendChild(node));
      pick(node); emit(); return;
    }
    if (!sel || d.cogi !== 'apply') return;
    const { prop, value } = d;
    if (prop === 'text') sel.textContent = value;
    else if (prop === 'fit') { sel.style.width = 'fit-content'; sel.style.height = 'auto'; dims(); } // 내용에 맞춤
    else if (prop === 'bgImage') { sel.style.backgroundImage = value ? 'url(' + value + ')' : ''; sel.style.backgroundSize = 'cover'; sel.style.backgroundPosition = 'center'; }
    else if (prop === 'nudge') {
      sel.style.position = sel.style.position || 'relative';
      sel.style.left = ((parseInt(sel.style.left) || 0) + value[0]) + 'px';
      sel.style.top = ((parseInt(sel.style.top) || 0) + value[1]) + 'px';
    } else if (prop === 'centerX') { sel.style.display = 'block'; sel.style.marginLeft = 'auto'; sel.style.marginRight = 'auto'; }
    else if (prop === 'layer') {
      if (value === 'up' && sel.nextElementSibling) sel.nextElementSibling.after(sel);
      if (value === 'down' && sel.previousElementSibling) sel.previousElementSibling.before(sel);
    } else if (prop === 'dup') { const copy = sel.cloneNode(true); sel.after(copy); pick(copy); }
    else if (prop === 'del') { const dead = sel; sel = null; layout(); dead.remove(); parent.postMessage({ cogi: 'clear' }, '*'); }
    else if (['rot', 'blur', 'bright', 'contrast', 'sat', 'gray'].includes(prop)) {
      const f = JSON.parse(sel.dataset.fx || '{}'); f[prop] = value; sel.dataset.fx = JSON.stringify(f); composeFx();
    } else sel.style[prop] = value;
    if (prop === 'width' || prop === 'height') dims();
    layout(); emit();
  });
})();
</script>`;

/* ── 패널 부품 ── */
const Prop = ({ label, children }) => (
  <label className="prop-row">
    <span>{label}</span>
    {children}
  </label>
);
// 아코디언: 한 번에 한 섹션만 — 열면 다른 섹션은 접힌다
const Sect = ({ id, title, children, openId, setOpenId }) => (
  <details
    className="prop-sect"
    open={openId === id}
    onToggle={(e) => {
      if ((e.currentTarget as HTMLDetailsElement).open && openId !== id)
        setOpenId(id);
    }}
  >
    <summary
      onClick={(e) => {
        e.preventDefault();
        setOpenId(openId === id ? null : id);
      }}
    >
      {title}
    </summary>
    {children}
  </details>
);

const FONTS = [
  ["inherit", "기본"],
  ["'Galmuri11', sans-serif", "갈무리(도트)"],
  ["sans-serif", "고딕"],
  ["serif", "명조"],
  ["monospace", "코드체"],
  ["system-ui", "시스템 둥근고딕"],
  ["cursive", "필기체"],
];
const GRADS = [
  ["", "없음(단색 유지)"],
  ["linear-gradient(135deg,#cfe8ff,#ffd23f)", "하늘→노랑"],
  ["linear-gradient(135deg,#ff6b57,#ffd23f)", "산호→노랑"],
  ["linear-gradient(180deg,#1b2a4a,#4ec9a4)", "네이비→민트"],
  ["linear-gradient(135deg,#7c5cff,#ff6bcb)", "보라→분홍"],
  ["linear-gradient(135deg,#4ec9a4,#cfe8ff)", "민트→하늘"],
  ["linear-gradient(135deg,#ff9a5a,#ff5a7e)", "노을"],
];
const SHADOWS_T = [
  ["none", "없음"],
  ["1px 1px 0 rgba(0,0,0,.35)", "또렷하게"],
  ["0 2px 6px rgba(0,0,0,.3)", "은은하게"],
  ["0 0 10px currentColor", "네온"],
];

export default function PreviewDock({ code, onCode }) {
  const [picked, setPicked] = useState(null);
  const [text, setText] = useState("");
  const [dim, setDim] = useState(null); // 선택 요소 W×H
  const [rotVal, setRotVal] = useState(0); // 회전(마우스 핸들 ↔ 슬라이더 양방향)
  const [changed, setChanged] = useState(new Set()); // #3 방금 바뀐 코드 라인 번호
  const prevCode = useRef(code);
  // 코드가 바뀔 때마다 이전 스냅샷과 라인 비교 → 달라진 줄만 하이라이트 (2초 후 소멸)
  useEffect(() => {
    const before = prevCode.current.split("\n");
    const after = code.split("\n");
    const diff = new Set();
    after.forEach((ln, i) => {
      if (ln !== before[i]) diff.add(i);
    });
    prevCode.current = code;
    if (diff.size) {
      setChanged(diff);
      const t = setTimeout(() => setChanged(new Set()), 2000);
      return () => clearTimeout(t);
    }
  }, [code]);
  const [full, setFull] = useState(false);
  const [openSect, setOpenSect] = useState("text"); // 아코디언: 열려있는 섹션 하나
  const [cols, setCols] = useState({ code: 30, props: 22 }); // 전체화면 3열 비율(%) — 리사이저로 조절
  const [zoom, setZoom] = useState(100);
  const skipNext = useRef(false);
  const frameRef = useRef(null);

  /* 실행취소/다시실행 — 코드 스냅샷 스택 (피그마의 Ctrl+Z 감각) */
  const hist = useRef({ stack: [code], idx: 0 });
  const pushHist = (c) => {
    const h = hist.current;
    if (h.stack[h.idx] === c) return;
    h.stack = h.stack
      .slice(0, h.idx + 1)
      .concat(c)
      .slice(-50);
    h.idx = h.stack.length - 1;
  };
  const timeTravel = (dir) => {
    const h = hist.current;
    const to = h.idx + dir;
    if (to < 0 || to >= h.stack.length) return;
    h.idx = to;
    onCode(h.stack[to]); // skipNext 미설정 → 프레임 재렌더로 시점 복원
    setPicked(null);
  };

  useEffect(() => {
    const onMsg = (e) => {
      const d = e.data || {};
      if (d.cogi === "pick") {
        setPicked(d);
        setText(d.text ?? "");
        frameRef.current?.focus();
      }
      if (d.cogi === "dim") setDim({ w: d.w, h: d.h });
      if (d.cogi === "clear") {
        setPicked(null);
        setDim(null);
      }
      if (d.cogi === "rot") setRotVal(d.deg); // 마우스 회전 → 슬라이더 동기화
      if (d.cogi === "code") {
        skipNext.current = true;
        onCode(d.code);
        pushHist(d.code);
      }
    };
    window.addEventListener("message", onMsg);
    return () => window.removeEventListener("message", onMsg);
  }, [onCode]);

  /* 부모 쪽 단축키: Ctrl+Z / Ctrl+Shift+Z (iframe 밖에서도 동작) */
  useEffect(() => {
    const onKey = (e) => {
      if (
        (e.ctrlKey || e.metaKey) &&
        (e.key.toLowerCase() === "z" || e.code === "KeyZ")
      ) {
        e.preventDefault();
        timeTravel(e.shiftKey ? 1 : -1);
        return;
      }
      if (full && e.key === "Escape") setFull(false);
      // 선택된 요소가 있으면 편집 키를 iframe 으로 전달 — 포커스가 부모에 있어도 방향키/Del/Ctrl+D 동작
      if (!picked) return;
      const t = e.target as HTMLElement;
      if (
        t &&
        (t.tagName === "INPUT" ||
          t.tagName === "TEXTAREA" ||
          t.tagName === "SELECT" ||
          t.isContentEditable)
      )
        return; // 입력 중엔 건드리지 않는다
      const step = e.shiftKey ? 10 : 1;
      const mv = {
        ArrowUp: [0, -step],
        ArrowDown: [0, step],
        ArrowLeft: [-step, 0],
        ArrowRight: [step, 0],
      }[e.key];
      if (mv) {
        e.preventDefault();
        apply("nudge", mv);
      } else if (e.key === "Delete" || e.key === "Backspace") {
        e.preventDefault();
        apply("del");
      } else if (
        (e.ctrlKey || e.metaKey) &&
        (e.key.toLowerCase() === "d" || e.code === "KeyD")
      ) {
        e.preventDefault();
        apply("dup");
      }
    };
    // 캡처 단계 + document: 포커스가 어디에 있어도(심지어 어디에도 없어도) 가장 먼저 받는다
    document.addEventListener("keydown", onKey, true);
    if (full) document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey, true);
      document.body.style.overflow = "";
    };
  }, [full, picked]);

  const [doc, setDoc] = useState(code);
  useEffect(() => {
    if (skipNext.current) {
      skipNext.current = false;
      return;
    }
    setDoc(code);
    setPicked(null);
    setDim(null);
    setRotVal(0);
  }, [code]);

  const apply = (prop: string, value?: any) =>
    frameRef.current?.contentWindow?.postMessage(
      { cogi: "apply", prop, value },
      "*",
    );
  const addEl = (kind) =>
    frameRef.current?.contentWindow?.postMessage({ cogi: "add", kind }, "*");

  const srcDoc = useMemo(() => doc + ENGINE, [doc]);
  const st = picked?.style;

  // 전체화면 리사이저: 경계선을 잡고 끌면 좌/우 열 너비(%)가 바뀐다
  const stageRef = useRef(null);
  const startResize = (side) => (e) => {
    e.preventDefault();
    const rect = stageRef.current.getBoundingClientRect();
    const onMove = (ev) => {
      const pct = ((ev.clientX - rect.left) / rect.width) * 100;
      setCols((c) =>
        side === "code"
          ? { ...c, code: Math.min(55, Math.max(15, pct)) }
          : { ...c, props: Math.min(40, Math.max(14, 100 - pct)) },
      );
    };
    const onUp = () => {
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
    };
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
  };

  return (
    <div className={`dock ${full ? "full" : ""}`}>
      {/* ── 툴바: 요소 추가 · 실행취소 · 줌 · 전체화면 ── */}
      <div className="dock-bar">
        <select
          className="dock-add"
          defaultValue=""
          aria-label="요소 추가"
          onChange={(e) => {
            if (e.target.value) {
              addEl(e.target.value);
              e.target.value = "";
            }
          }}
        >
          <option value="" disabled>
            ＋ 요소 추가
          </option>
          <option value="text">텍스트</option>
          <option value="button">버튼</option>
          <option value="box">박스</option>
          <option value="image">이미지</option>
          <option value="divider">구분선</option>
          <option value="input">입력창</option>
          <option value="badge">뱃지</option>
          <option value="card">카드</option>
        </select>
        <button
          className="dock-tool"
          title="실행취소 (Ctrl+Z)"
          onClick={() => timeTravel(-1)}
        >
          ↩
        </button>
        <button
          className="dock-tool"
          title="다시실행 (Ctrl+Shift+Z)"
          onClick={() => timeTravel(1)}
        >
          ↪
        </button>
        <span className="dock-help" aria-label="사용법">
          <i>
            <b>클릭</b> 선택
          </i>
          <i>
            <b>드래그</b> 이동
          </i>
          <i>
            <b>8점 핸들</b> 크기
          </i>
          <i>
            <b>🟡 핸들</b> 회전
          </i>
          <i>
            <b>방향키</b> 이동
          </i>
          <i>
            <b>Del</b> 삭제
          </i>
          <i>
            <b>Ctrl+D</b> 복제
          </i>
        </span>
        <select
          className="dock-add"
          value={zoom}
          aria-label="줌"
          onChange={(e) => setZoom(Number(e.target.value))}
        >
          {[50, 75, 100, 125, 150].map((z) => (
            <option key={z} value={z}>
              {z}%
            </option>
          ))}
        </select>
        <button
          className="dock-full-btn"
          title={full ? "창 모드로 (Esc)" : "전체화면으로"}
          onClick={() => setFull((f) => !f)}
        >
          {full ? "⤡ 창 모드" : "⤢ 전체화면"}
        </button>
      </div>

      <div
        className="dock-stage"
        ref={stageRef}
        style={
          full
            ? {
                gridTemplateColumns: `${cols.code}% 6px 1fr 6px ${cols.props}%`,
              }
            : undefined
        }
      >
        {/* 전체화면 전용: 코드가 제일 왼쪽 — 크게 보면서 수정 */}
        {full && (
          <>
            <div className="dock-code-wrap side">
              <pre className="dock-code-hl" aria-hidden="true">
                {code.split("\n").map((ln, i) => (
                  <div key={i} className={changed.has(i) ? "chg" : ""}>
                    {ln || " "}
                  </div>
                ))}
              </pre>
              <textarea
                className="dock-code side"
                value={code}
                spellCheck={false}
                onChange={(e) => {
                  onCode(e.target.value);
                  pushHist(e.target.value);
                }}
                aria-label="코드 (실시간 동기화)"
              />
            </div>
            <div
              className="dock-resizer"
              onMouseDown={startResize("code")}
              title="드래그로 폭 조절"
            />
          </>
        )}
        <div className="dock-canvas">
          <div
            className="dock-scale"
            style={{
              width: `${10000 / zoom}%`,
              height: full ? `${10000 / zoom}%` : undefined,
              transform: `scale(${zoom / 100})`,
            }}
          >
            <iframe
              ref={frameRef}
              title="라이브 미리보기"
              className="dock-frame"
              sandbox="allow-scripts allow-same-origin"
              srcDoc={srcDoc}
            />
          </div>
        </div>

        {full && (
          <div
            className="dock-resizer"
            onMouseDown={startResize("props")}
            title="드래그로 폭 조절"
          />
        )}
        {/* ── 속성 패널: 텍스트 / 배치 / 스타일 / 효과 (아코디언) ── */}
        <aside className="dock-props">
          {picked ? (
            <>
              <div className="row" style={{ marginBottom: 10 }}>
                <span className="chip navy">{picked.label}</span>
                {dim && (
                  <span className="chip gray">
                    {dim.w}×{dim.h}
                  </span>
                )}
              </div>

              <Sect
                id="text"
                title="텍스트"
                openId={openSect}
                setOpenId={setOpenSect}
              >
                {picked.text !== null && (
                  <Prop label="내용">
                    <input
                      type="text"
                      value={text}
                      onChange={(e) => {
                        setText(e.target.value);
                        apply("text", e.target.value);
                      }}
                    />
                  </Prop>
                )}
                <Prop label="글꼴">
                  <select
                    defaultValue="inherit"
                    onChange={(e) => apply("fontFamily", e.target.value)}
                  >
                    {FONTS.map(([v, ko]) => (
                      <option key={ko} value={v}>
                        {ko}
                      </option>
                    ))}
                  </select>
                </Prop>
                <Prop label="크기">
                  <input
                    type="number"
                    min="8"
                    max="120"
                    defaultValue={st.fontSize}
                    onChange={(e) => apply("fontSize", e.target.value + "px")}
                  />
                </Prop>
                <Prop label="굵기">
                  <select
                    defaultValue={st.fontWeight >= 600 ? "bold" : "normal"}
                    onChange={(e) => apply("fontWeight", e.target.value)}
                  >
                    <option value="300">얇게</option>
                    <option value="normal">보통</option>
                    <option value="bold">굵게</option>
                    <option value="900">아주 굵게</option>
                  </select>
                </Prop>
                <Prop label="색">
                  <input
                    type="color"
                    defaultValue={st.color}
                    onChange={(e) => apply("color", e.target.value)}
                  />
                </Prop>
                <Prop label="정렬">
                  <span className="btn3">
                    <button onClick={() => apply("textAlign", "left")}>
                      ⇤
                    </button>
                    <button onClick={() => apply("textAlign", "center")}>
                      ≡
                    </button>
                    <button onClick={() => apply("textAlign", "right")}>
                      ⇥
                    </button>
                  </span>
                </Prop>
                <Prop label="꾸밈">
                  <span className="btn3">
                    <button
                      style={{ fontStyle: "italic" }}
                      onClick={() => apply("fontStyle", "italic")}
                    >
                      I
                    </button>
                    <button
                      style={{ textDecoration: "underline" }}
                      onClick={() => apply("textDecoration", "underline")}
                    >
                      U
                    </button>
                    <button
                      style={{ textDecoration: "line-through" }}
                      onClick={() => apply("textDecoration", "line-through")}
                    >
                      S
                    </button>
                  </span>
                </Prop>
                <Prop label="자간">
                  <input
                    type="number"
                    step="0.5"
                    min="-3"
                    max="24"
                    defaultValue={st.letterSpacing}
                    onChange={(e) =>
                      apply("letterSpacing", e.target.value + "px")
                    }
                  />
                </Prop>
                <Prop label="줄간격">
                  <input
                    type="number"
                    step="0.1"
                    min="1"
                    max="3"
                    defaultValue="1.6"
                    onChange={(e) => apply("lineHeight", e.target.value)}
                  />
                </Prop>
                <Prop label="글자 그림자">
                  <select
                    defaultValue="none"
                    onChange={(e) => apply("textShadow", e.target.value)}
                  >
                    {SHADOWS_T.map(([v, ko]) => (
                      <option key={ko} value={v}>
                        {ko}
                      </option>
                    ))}
                  </select>
                </Prop>
                <Prop label="대소문자">
                  <span className="btn3">
                    <button
                      title="모두 대문자"
                      onClick={() => apply("textTransform", "uppercase")}
                    >
                      AA
                    </button>
                    <button
                      title="원래대로"
                      onClick={() => apply("textTransform", "none")}
                    >
                      Aa
                    </button>
                  </span>
                </Prop>
                <Prop label="글자 그라데이션">
                  <select
                    defaultValue=""
                    onChange={(e) => {
                      if (!e.target.value) {
                        apply("backgroundImage", "none");
                        apply("WebkitBackgroundClip", "");
                        apply("WebkitTextFillColor", "");
                        return;
                      }
                      apply("backgroundImage", e.target.value);
                      apply("WebkitBackgroundClip", "text");
                      apply("WebkitTextFillColor", "transparent");
                    }}
                  >
                    {GRADS.map(([v, ko]) => (
                      <option key={ko} value={v}>
                        {ko}
                      </option>
                    ))}
                  </select>
                </Prop>
              </Sect>

              {/* 컨테이너 배치(flex) — 박스·카드 안 요소를 웹 레이아웃처럼 정렬 */}
              <Sect
                id="flex"
                title="컨테이너 정렬"
                openId={openSect}
                setOpenId={setOpenSect}
              >
                <Prop label="배치 방향">
                  <span className="btn3">
                    <button
                      title="가로 배치"
                      onClick={() => {
                        apply("display", "flex");
                        apply("flexDirection", "row");
                      }}
                    >
                      ⇄
                    </button>
                    <button
                      title="세로 배치"
                      onClick={() => {
                        apply("display", "flex");
                        apply("flexDirection", "column");
                      }}
                    >
                      ⇅
                    </button>
                    <button
                      title="배치 해제"
                      onClick={() => apply("display", "")}
                    >
                      ✕
                    </button>
                  </span>
                </Prop>
                <Prop label="주축 정렬">
                  <select
                    defaultValue="flex-start"
                    onChange={(e) => apply("justifyContent", e.target.value)}
                  >
                    <option value="flex-start">시작</option>
                    <option value="center">가운데</option>
                    <option value="flex-end">끝</option>
                    <option value="space-between">양끝 분산</option>
                    <option value="space-evenly">고른 간격</option>
                  </select>
                </Prop>
                <Prop label="교차 정렬">
                  <select
                    defaultValue="stretch"
                    onChange={(e) => apply("alignItems", e.target.value)}
                  >
                    <option value="stretch">늘림</option>
                    <option value="flex-start">시작</option>
                    <option value="center">가운데</option>
                    <option value="flex-end">끝</option>
                  </select>
                </Prop>
                <Prop label="간격(px)">
                  <input
                    type="number"
                    min="0"
                    max="80"
                    defaultValue="0"
                    onChange={(e) => apply("gap", e.target.value + "px")}
                  />
                </Prop>
                <Prop label="줄바꿈">
                  <span className="btn3">
                    <button
                      title="넘치면 줄바꿈"
                      onClick={() => apply("flexWrap", "wrap")}
                    >
                      ↩
                    </button>
                    <button
                      title="한 줄 유지"
                      onClick={() => apply("flexWrap", "nowrap")}
                    >
                      —
                    </button>
                  </span>
                </Prop>
              </Sect>

              <Sect
                id="layout"
                title="배치"
                openId={openSect}
                setOpenId={setOpenSect}
              >
                <Prop label="너비">
                  <input
                    type="number"
                    min="0"
                    max="1600"
                    defaultValue={dim?.w}
                    onChange={(e) =>
                      apply(
                        "width",
                        e.target.value ? e.target.value + "px" : "auto",
                      )
                    }
                  />
                </Prop>
                <Prop label="높이">
                  <input
                    type="number"
                    min="0"
                    max="1200"
                    defaultValue={dim?.h}
                    onChange={(e) =>
                      apply(
                        "height",
                        e.target.value ? e.target.value + "px" : "auto",
                      )
                    }
                  />
                </Prop>
                <Prop label="안쪽 여백">
                  <input
                    type="number"
                    min="0"
                    max="160"
                    defaultValue={st.padding}
                    onChange={(e) => apply("padding", e.target.value + "px")}
                  />
                </Prop>
                <Prop label="바깥 여백">
                  <input
                    type="number"
                    min="0"
                    max="160"
                    defaultValue={st.margin}
                    onChange={(e) => apply("margin", e.target.value + "px")}
                  />
                </Prop>
                <Prop label="회전(°)">
                  <span className="rot-ctrl">
                    <input
                      type="range"
                      min="-180"
                      max="180"
                      value={rotVal}
                      onChange={(e) => {
                        setRotVal(Number(e.target.value));
                        apply("rot", Number(e.target.value));
                      }}
                    />
                    <input
                      type="number"
                      min="-180"
                      max="180"
                      value={rotVal}
                      className="rot-num"
                      onChange={(e) => {
                        const v = Number(e.target.value) || 0;
                        setRotVal(v);
                        apply("rot", v);
                      }}
                    />
                  </span>
                </Prop>
                <Prop label="크기 맞춤">
                  <button
                    className="fit-btn"
                    title="상자를 내용 길이에 딱 맞게"
                    onClick={() => apply("fit")}
                  >
                    내용에 맞춤
                  </button>
                </Prop>
                <Prop label="정렬·겹침">
                  <span className="btn3">
                    <button
                      title="가로 가운데"
                      onClick={() => apply("centerX")}
                    >
                      ▣
                    </button>
                    <button
                      title="한 층 앞으로"
                      onClick={() => apply("layer", "up")}
                    >
                      ⬆
                    </button>
                    <button
                      title="한 층 뒤로"
                      onClick={() => apply("layer", "down")}
                    >
                      ⬇
                    </button>
                  </span>
                </Prop>
                <Prop label="복제·삭제">
                  <span className="btn3">
                    <button title="복제 (Ctrl+D)" onClick={() => apply("dup")}>
                      ⧉
                    </button>
                    <button title="삭제 (Del)" onClick={() => apply("del")}>
                      🗑
                    </button>
                  </span>
                </Prop>
              </Sect>

              <Sect
                id="style"
                title="스타일"
                openId={openSect}
                setOpenId={setOpenSect}
              >
                <Prop label="배경색">
                  <input
                    type="color"
                    defaultValue={st.background}
                    onChange={(e) => apply("background", e.target.value)}
                  />
                </Prop>
                <Prop label="배경 이미지">
                  <input
                    type="text"
                    placeholder="이미지 URL"
                    onKeyDown={(e) => {
                      if (e.key === "Enter")
                        apply("bgImage", (e.target as HTMLInputElement).value);
                    }}
                    onBlur={(e) =>
                      e.target.value && apply("bgImage", e.target.value)
                    }
                  />
                </Prop>
                <Prop label="그라데이션">
                  <select
                    defaultValue=""
                    onChange={(e) =>
                      e.target.value && apply("background", e.target.value)
                    }
                  >
                    {GRADS.map(([v, ko]) => (
                      <option key={ko} value={v}>
                        {ko}
                      </option>
                    ))}
                  </select>
                </Prop>
                <Prop label="테두리 굵기">
                  <input
                    type="number"
                    min="0"
                    max="12"
                    defaultValue={st.borderW}
                    onChange={(e) =>
                      apply(
                        "border",
                        Number(e.target.value)
                          ? `${e.target.value}px solid ${st.borderC}`
                          : "none",
                      )
                    }
                  />
                </Prop>
                <Prop label="테두리 색">
                  <input
                    type="color"
                    defaultValue={st.borderC}
                    onChange={(e) =>
                      apply(
                        "border",
                        `${st.borderW || 2}px solid ${e.target.value}`,
                      )
                    }
                  />
                </Prop>
                <Prop label="테두리 스타일">
                  <select
                    defaultValue="solid"
                    onChange={(e) => apply("borderStyle", e.target.value)}
                  >
                    <option value="solid">실선</option>
                    <option value="dashed">파선</option>
                    <option value="dotted">점선</option>
                    <option value="double">이중선</option>
                  </select>
                </Prop>
                <Prop label="둥글기">
                  <input
                    type="number"
                    min="0"
                    max="100"
                    defaultValue={st.radius}
                    onChange={(e) =>
                      apply("borderRadius", e.target.value + "px")
                    }
                  />
                </Prop>
                <Prop label="그림자">
                  <select
                    defaultValue=""
                    onChange={(e) => apply("boxShadow", e.target.value)}
                  >
                    <option value="none">없음</option>
                    <option value="4px 4px 0 #1b2a4a">픽셀 하드섀도우</option>
                    <option value="0 4px 14px rgba(0,0,0,.18)">부드럽게</option>
                    <option value="0 12px 34px rgba(0,0,0,.28)">크게</option>
                    <option value="inset 0 3px 8px rgba(0,0,0,.25)">
                      안쪽
                    </option>
                  </select>
                </Prop>
                <Prop label="투명도(%)">
                  <input
                    type="range"
                    min="10"
                    max="100"
                    defaultValue={st.opacity}
                    onChange={(e) =>
                      apply("opacity", Number(e.target.value) / 100)
                    }
                  />
                </Prop>
              </Sect>

              <Sect
                id="fx"
                title="효과 (필터)"
                openId={openSect}
                setOpenId={setOpenSect}
              >
                <Prop label="블러">
                  <input
                    type="range"
                    min="0"
                    max="12"
                    defaultValue="0"
                    onChange={(e) => apply("blur", Number(e.target.value))}
                  />
                </Prop>
                <Prop label="밝기(%)">
                  <input
                    type="range"
                    min="40"
                    max="180"
                    defaultValue="100"
                    onChange={(e) => apply("bright", Number(e.target.value))}
                  />
                </Prop>
                <Prop label="대비(%)">
                  <input
                    type="range"
                    min="40"
                    max="180"
                    defaultValue="100"
                    onChange={(e) => apply("contrast", Number(e.target.value))}
                  />
                </Prop>
                <Prop label="채도(%)">
                  <input
                    type="range"
                    min="0"
                    max="200"
                    defaultValue="100"
                    onChange={(e) => apply("sat", Number(e.target.value))}
                  />
                </Prop>
                <Prop label="흑백(%)">
                  <input
                    type="range"
                    min="0"
                    max="100"
                    defaultValue="0"
                    onChange={(e) => apply("gray", Number(e.target.value))}
                  />
                </Prop>
              </Sect>
            </>
          ) : (
            <div className="dock-guide">
              <b>이렇게 편집해요</b>
              <ol>
                <li>
                  <b>클릭</b> — 요소 선택
                </li>
                <li>
                  <b>드래그</b> — 잡아서 이동
                </li>
                <li>
                  <b>8점 핸들</b> — 크기 조절
                </li>
                <li>
                  <b>🟡 노란 핸들</b> — 회전 (Shift=15°)
                </li>
                <li>
                  <b>방향키</b> — 1px 이동 (Shift=10px)
                </li>
                <li>
                  <b>Del</b> 삭제 · <b>Ctrl+D</b> 복제
                </li>
              </ol>
              <p className="note xs">
                <b>Ctrl+Z</b> 실행취소 · 편집하면 왼쪽 코드가 실시간으로
                바뀝니다.
              </p>
            </div>
          )}
        </aside>
      </div>

      {/* 변경 라인 하이라이트: 뒤에 색칠된 레이어를 깔고 그 위에 투명 textarea */}
      {!full && (
        <div className="dock-code-wrap">
          <pre className="dock-code-hl" aria-hidden="true">
            {code.split("\n").map((ln, i) => (
              <div key={i} className={changed.has(i) ? "chg" : ""}>
                {ln || " "}
              </div>
            ))}
          </pre>
          <textarea
            className="dock-code"
            value={code}
            spellCheck={false}
            onChange={(e) => {
              onCode(e.target.value);
              pushHist(e.target.value);
            }}
            aria-label="미리보기 코드 (실시간 동기화)"
          />
        </div>
      )}
    </div>
  );
}
