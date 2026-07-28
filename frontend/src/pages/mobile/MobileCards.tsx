/*
 * 모바일 전용 학습카드 목록.
 * 데스크톱은 2열 격자인데 폰에서는 카드가 좁아 글자가 접힌다. 한 줄에 하나씩 리스트로 깐다.
 * 기능은 데스크톱과 같다 — 등급·북마크 필터, 카드 상세로 이동, 콜드스타트 안내.
 */
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import * as api from "../../api/client";
import { catKo } from "../../data/constants";
import "../../styles/mobile/cards.css";

// 데스크톱 탭과 같은 필터 목록
const FILTERS: [string, string][] = [
  ["ALL", "전체"], ["RED", "시작"], ["YELLOW", "진행중"], ["GREEN", "해결"], ["BOOKMARK", "북마크"],
];

// 등급 → 신호등 색과 라벨
const GRADE = {
  RED: ["red", "시작"], YELLOW: ["yellow", "진행중"],
  GREEN: ["green", "해결"], GREEN_PLUS: ["green", "해결+"],
};

export default function MobileCards() {
  const [cards, setCards] = useState(null);
  const [f, setF] = useState("ALL");

  useEffect(() => { api.getLearningCards().then(setCards).catch(() => setCards([])); }, []);

  const shown = (cards || []).filter((c) => {
    if (f === "BOOKMARK") return c.isBookmarked;
    if (f !== "ALL") return c.grade === f || (f === "GREEN" && c.grade === "GREEN_PLUS");
    return true;
  });

  if (!cards) return <main className="mapp"><p className="mnote">불러오는 중…</p></main>;

  return (
    <main className="mapp">
      {/* 필터 — 드롭다운 대신 세그먼트 */}
      <div className="mseg mc-seg">
        {FILTERS.map(([k, label]) => (
          <button key={k} className={f === k ? "on" : ""} onClick={() => setF(k)}>{label}</button>
        ))}
      </div>

      {shown.length === 0 ? (
        <section className="mcard mempty">
          <p>{cards.length === 0
            ? "아직 카드가 없어요.\n약점 페이지에서 만들어 보세요."
            : "이 조건의 카드가 없어요."}</p>
          <Link className="btn co sm full" to="/app/weakness">약점 통계로</Link>
        </section>
      ) : (
        <ul className="mcards">
          {shown.map((c) => {
            const [tone, gradeLabel] = GRADE[c.grade] ?? ["red", c.grade];
            return (
              <li key={c.id}>
                <Link to={`/app/cards/${c.id}`}>
                  <div className="mc-top">
                    <span className={`mc-light ${tone}`}>{gradeLabel}</span>
                    {c.isBookmarked && <span className="mc-bm">★</span>}
                    {c.isCompleted && <span className="mc-done">완료</span>}
                    <span className="mc-go">›</span>
                  </div>
                  <b className="mc-title">{catKo(c.category)}</b>
                  <div className="mc-meta">
                    <span>{c.language}</span>
                    <span className="dot">·</span>
                    <span>{c.level}</span>
                    <span className="dot">·</span>
                    <span>정답 {c.correctCount}회</span>
                  </div>
                  {/* 승급까지 얼마나 남았는지 막대로 — 목록에서도 진행이 보이게 */}
                  <div className="mc-bar"><i style={{ width: `${Math.min(100, (c.correctCount / 8) * 100)}%` }} /></div>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </main>
  );
}
