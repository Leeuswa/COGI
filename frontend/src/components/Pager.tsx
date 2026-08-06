/*
 * 클라이언트 페이지네이션 — 목록 전체를 이미 받아온 화면에서 보이는 부분만 잘라 준다.
 * 공지는 서버가 전량을 주고(자동 삭제도 없어 계속 쌓인다) 건수가 크지 않아 서버 페이징까지 갈 필요가 없다.
 * ponytail: 전량 로드 전제. 수천 건 규모가 되면 서버 페이징(Pageable)으로 올려야 한다.
 */

// page는 1부터. 범위를 넘는 값은 마지막 페이지로 접어준다(목록이 줄어든 뒤에도 빈 화면이 안 뜨게).
// 호출부의 목록 props가 대부분 untyped라 제네릭을 두면 T가 unknown으로 좁혀져 필드 접근이 막힌다.
export function pageSlice(items: any[], page: number, size: number): any[] {
  const cur = clamp(page, items.length, size);
  return items.slice((cur - 1) * size, cur * size);
}

function clamp(page: number, total: number, size: number) {
  const last = Math.max(1, Math.ceil(total / size));
  return Math.min(Math.max(1, page), last);
}

export default function Pager({ page, total, size, onChange }) {
  const last = Math.max(1, Math.ceil(total / size));
  if (last <= 1) return null; // 한 페이지면 굳이 안 보여준다

  const cur = clamp(page, total, size);
  // 현재 페이지 주변 5개만 노출 — 페이지가 많아져도 줄바꿈으로 번지지 않게
  const start = Math.max(1, Math.min(cur - 2, last - 4));
  const nums = [];
  for (let p = start; p < start + 5 && p <= last; p++) nums.push(p);

  return (
    <div className="row" style={{ justifyContent: 'center', gap: 6, padding: '14px 12px', flexWrap: 'wrap' }}>
      <button type="button" className="btn wh sm" disabled={cur === 1} onClick={() => onChange(cur - 1)}>이전</button>
      {nums.map((p) => (
        <button key={p} type="button" className={`btn ${p === cur ? 'co' : 'wh'} sm`}
          aria-current={p === cur ? 'page' : undefined} onClick={() => onChange(p)}>
          {p}
        </button>
      ))}
      <button type="button" className="btn wh sm" disabled={cur === last} onClick={() => onChange(cur + 1)}>다음</button>
    </div>
  );
}
