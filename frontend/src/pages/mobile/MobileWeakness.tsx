/*
 * 모바일 전용 약점 통계.
 * 데스크톱은 한 줄에 [횟수][카테고리][버튼]을 늘어놓는데 폰에서는 버튼이 카드 밖으로 밀린다.
 * 카드 안에서 위아래로 나누고 버튼은 폭을 꽉 채운다.
 * 기능은 데스크톱과 같다 — 학습카드 생성(크레딧 1)·이미 만든 카드로 이동·콜드스타트 안내.
 */
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import * as api from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useGame } from "../../context/GameContext";
import { catKo } from "../../data/constants";
import "../../styles/mobile/weakness.css";

export default function MobileWeakness() {
  const { user } = useAuth();
  const { spendCredit } = useGame();
  const nav = useNavigate();

  const [list, setList] = useState(null);
  const [creating, setCreating] = useState(null);
  // 실패를 빈 목록으로 삼키면 "약점이 없다"는 콜드스타트 화면과 구분이 안 된다
  const [failed, setFailed] = useState(false);

  useEffect(() => { api.getWeaknessStats().then(setList).catch(() => setFailed(true)); }, []);

  if (failed) {
    return (
      <main className="mapp">
        <section className="mcard mempty">
          <p>약점을 불러오지 못했어요.<br />잠시 뒤 다시 열어봐 주세요.</p>
        </section>
      </main>
    );
  }

  // 약점 → 학습카드 생성 (내 수준 + 해당 언어 반영, 크레딧 1개)
  const makeCard = async (w) => {
    if (creating) return;
    if (!spendCredit(1)) return;
    setCreating(w.id);
    try {
      const card = await api.createLearningCard(w.category, user.level || "BEGINNER", w.language);
      setList((prev) => prev.map((x) => (x.id === w.id ? { ...x, cardId: card.id } : x)));
      nav(`/app/cards/${card.id}`);
    } finally { setCreating(null); }
  };

  if (!list) return <main className="mapp"><p className="mnote">집계 중…</p></main>;

  // 콜드스타트 (FR-57)
  if (list.length === 0) {
    return (
      <main className="mapp">
        <section className="mcard mempty">
          <p>아직 약점 데이터가 없어요.<br />리뷰를 몇 번 받으면 반복 실수가 여기 모입니다.</p>
          <Link className="btn co sm full" to="/app/paste">코드 리뷰 받기</Link>
          <Link className="btn wh sm full mw-second" to="/app/repos">레포 연동하기</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="mapp">
      <p className="mlead">같은 지적이 3번 반복되면 약점으로 올라와요.</p>

      {list.map((w) => (
        <section key={w.id} className="mcard mw-card">
          <div className="mw-top">
            <span className="mw-count"><b>{w.occurrenceCount}</b><i>회</i></span>
            <span className="mw-body">
              <b>{catKo(w.category)}</b>
              <i>{w.language} · 마지막 {w.updatedAt?.slice(5, 10).replace("-", ".")}</i>
            </span>
          </div>

          {/* 카드가 이미 있으면 만들기 대신 그 카드로 (중복 생성·크레딧 낭비 방지) */}
          {w.cardId ? (
            <Link className="btn wh sm full" to={`/app/cards/${w.cardId}`}>📗 학습카드 보기</Link>
          ) : (
            <button className="btn co sm full" disabled={creating === w.id} onClick={() => makeCard(w)}>
              {creating === w.id ? "카드 굽는 중…" : "학습카드 만들기 (⚡1)"}
            </button>
          )}
          {/* 이 약점 카테고리로 걸러진 스킬 목록으로 — 큐레이션 조회라 크레딧 안 든다 */}
          <Link className="btn wh sm full" to={`/app/skills?category=${w.category}`}>🤖 AI 스킬 추천</Link>
        </section>
      ))}

      <p className="mnote mw-foot">
        이미 만든 카드는 <Link to="/app/cards">학습카드 목록</Link>에서 볼 수 있어요.
      </p>
    </main>
  );
}
