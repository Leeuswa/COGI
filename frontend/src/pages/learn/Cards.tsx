/*
 * 학습카드 목록 (LRN-002 목록 뷰)
 * 신호등 등급 + 북마크/완료 상태를 한눈에. 필터는 등급/북마크만 (더 필요하면 그때 추가).
 * 카드 생성 진입점은 약점 페이지 — 여기선 보관함 역할.
 */
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/client';
import { PageHead, GradeLight } from '../../components/ui';
import { SPR } from '../../assets/sprites';

export default function Cards() {
  const [cards, setCards] = useState(null);
  const [f, setF] = useState('ALL'); // ALL / RED / YELLOW / GREEN / BOOKMARK

  useEffect(() => { api.getLearningCards().then(setCards); }, []);

  const shown = (cards || []).filter((c) => {
    if (f === 'BOOKMARK') return c.isBookmarked;
    if (f !== 'ALL') return c.grade === f;
    return true;
  });

  return (
    <main className="app-main">
      <PageHead
        badge="LEARNING CARDS"
        title="학습카드 보관함"
        lead={"빨강에서 시작해 정답 3번이면 노랑, 7번이면 초록이에요.\n초록이 되면 그 약점은 해결된 걸로 처리됩니다."}
      />

      <div className="tabs">
        {[['ALL', '전체'], ['RED', '🔴 시작'], ['YELLOW', '🟡 진행중'], ['GREEN', '🟢 해결'], ['BOOKMARK', '★ 북마크']].map(([k, label]) => (
          <button key={k} className={f === k ? 'on' : ''} onClick={() => setF(k)}>{label}</button>
        ))}
      </div>

      {!cards ? (
        <div className="panel"><p className="note">불러오는 중…</p></div>
      ) : shown.length === 0 ? (
        <div className="empty">
          <img src={SPR.egg} alt="" />
          <p>{f === 'ALL' ? '아직 카드가 없어요. 약점 페이지에서 만들어보세요.' : '이 조건의 카드가 없어요.'}</p>
          <Link className="btn co sm" style={{ marginTop: 12, display: 'inline-block' }} to="/app/weakness">약점 통계 →</Link>
        </div>
      ) : (
        <div className="panel-grid c2">
          {shown.map((c) => (
            <Link key={c.id} to={`/app/cards/${c.id}`} className="panel" style={{ display: 'block' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
                <GradeLight grade={c.grade} />
                {c.isBookmarked && <span title="북마크">★</span>}
                {c.isCompleted && <span className="chip low">완료</span>}
              </div>
              <b style={{ fontSize: 15 }}>{c.category}</b>
              <p className="note sm" style={{ marginTop: 6 }}>
                {c.language} · {c.level} · 정답 {c.correctCount}회
              </p>
            </Link>
          ))}
        </div>
      )}
    </main>
  );
}
