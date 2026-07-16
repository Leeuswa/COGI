/*
 * 약점 통계 (LRN-001, API-042, FR-56~57)
 * 동일 카테고리 이슈가 3회 이상 반복될 때만 여기 올라온다.
 * 데이터가 없으면 콜드스타트: 슬픈 코기 + "리뷰부터 받아보세요" 안내 (FR-57 그대로).
 * 각 약점에서 바로 학습카드 생성으로 이어진다 (LRN-002 진입점).
 */
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as api from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { useGame } from '../../context/GameContext';
import { PageHead } from '../../components/ui';
import { SPR } from '../../assets/sprites';

export default function Weakness() {
  const { user } = useAuth();
  const { spendCredit } = useGame();
  const nav = useNavigate();
  const [list, setList] = useState(null);
  const [creating, setCreating] = useState(null); // 생성 중인 weakness id

  useEffect(() => { api.getWeaknessStats().then(setList); }, []);

  // 약점 → 학습카드 생성 (내 수준 + 해당 언어 반영, 크레딧 1개)
  const makeCard = async (w) => {
    if (creating) return;
    if (!spendCredit(1)) return;
    setCreating(w.id);
    try {
      const card = await api.createLearningCard(w.category, user.level || 'BEGINNER', w.language);
      setList((prev) => prev.map((x) => (x.id === w.id ? { ...x, cardId: card.id } : x))); // 버튼 → 카드 링크로 전환
      nav(`/app/cards/${card.id}`);
    } finally { setCreating(null); }
  };

  return (
    <main className="app-main">
      <PageHead
        badge="WEAKNESS" badgeCls="co"
        title="내 약점 통계"
        lead={"같은 유형의 지적이 3번 반복되면 여기에 약점으로 올라와요.\n학습카드로 정면돌파하는 게 제일 빠릅니다."}
      />

      {!list ? (
        <div className="panel"><p className="note">집계 중…</p></div>
      ) : list.length === 0 ? (
        // 콜드스타트 (FR-57) — 리뷰 데이터가 아직 없는 신규 유저
        <div className="empty">
          <img src={SPR.sad} alt="" />
          <p>아직 약점 데이터가 없어요.<br />리뷰를 몇 번 받으면 반복 실수가 여기 모입니다.</p>
          <div style={{ marginTop: 16, display: 'flex', gap: 10, justifyContent: 'center' }}>
            <Link className="btn co sm" to="/app/paste">코드 리뷰 받기</Link>
            <Link className="btn wh sm" to="/app/repos">레포 연동하기</Link>
          </div>
        </div>
      ) : (
        list.map((w) => (
          <div key={w.id} className="panel weak-card">
            {/* 반복 횟수가 이 카드의 핵심 정보 — 크고 또렷하게 */}
            <div className={`weak-count ${w.occurrenceCount < 4 ? 'mild' : ''}`}>
              <b>{w.occurrenceCount}회</b>
              <span>반복</span>
            </div>
            <div className="weak-body">
              <b>{w.category}</b>
              <p className="note sm">{w.language} · 마지막 발생 {w.updatedAt}</p>
            </div>
            <div className="row" style={{ flexShrink: 0 }}>
              {/* 카드가 이미 있으면 만들기 대신 그 카드로 (중복 생성·크레딧 낭비 방지) */}
              {w.cardId ? (
                <Link className="btn sm" to={`/app/cards/${w.cardId}`}>📗 학습카드 보기 →</Link>
              ) : (
                <button className="btn co sm" onClick={() => makeCard(w)} disabled={creating === w.id}>
                  {creating === w.id ? '카드 굽는 중…' : '학습카드 만들기 (⚡1)'}
                </button>
              )}
            </div>
          </div>
        ))
      )}

      {list && list.length > 0 && (
        <p className="note sm mt18">
          이미 만든 카드는 <Link to="/app/cards" className="link-line">학습카드 목록</Link>에서 볼 수 있어요.
        </p>
      )}
    </main>
  );
}
