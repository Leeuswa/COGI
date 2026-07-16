/*
 * 강의 추천 (LRN-003~004, API-048, FR-66~69)
 * 크롤링 배치가 저장해둔 인프런/유데미/공식 문서를 약점 기준으로 추천.
 * 필터: 가격(무료/유료) / 시간 / 난이도. 필터 값은 쿼리로 서버에 그대로 간다.
 */
import { useEffect, useState } from 'react';
import * as api from '../../api/client';
import { PageHead } from '../../components/ui';
import { SPR } from '../../assets/sprites';

const PLATFORM_LABEL = { INFLEARN: '인프런', UDEMY: 'Udemy', OFFICIAL: '공식 문서' };

export default function Courses() {
  const [courses, setCourses] = useState(null);
  const [f, setF] = useState({ price: 'ALL', duration: 'ALL', level: 'ALL' });

  useEffect(() => { api.getCourses(f).then(setCourses); }, [f]);

  const set = (k) => (e) => setF((p) => ({ ...p, [k]: e.target.value }));

  // 목 모드 클라이언트 필터 (서버 전환 시 그냥 통과)
  const shown = (courses || []).filter((c) => {
    if (f.price === 'FREE' && c.price > 0) return false;
    if (f.price === 'PAID' && c.price === 0) return false;
    if (f.duration === 'SHORT' && c.duration > 180) return false;
    if (f.duration === 'LONG' && c.duration <= 180) return false;
    if (f.level !== 'ALL' && c.level !== f.level) return false;
    return true;
  });

  const hours = (min) => (min >= 60 ? `${Math.floor(min / 60)}시간 ${min % 60 ? (min % 60) + '분' : ''}` : `${min}분`);

  return (
    <main className="app-main">
      <PageHead
        badge="COURSES"
        title="약점 맞춤 강의 추천"
        lead="약점 카테고리 기준으로 인프런 · Udemy · 공식 문서에서 골라왔어요."
      />

      <div className="filter-bar">
        <select value={f.price} onChange={set('price')}>
          <option value="ALL">가격 전체</option>
          <option value="FREE">무료</option>
          <option value="PAID">유료</option>
        </select>
        <select value={f.duration} onChange={set('duration')}>
          <option value="ALL">시간 전체</option>
          <option value="SHORT">3시간 이하</option>
          <option value="LONG">3시간 초과</option>
        </select>
        <select value={f.level} onChange={set('level')}>
          <option value="ALL">난이도 전체</option>
          <option value="BEGINNER">초급</option>
          <option value="INTERMEDIATE">중급</option>
          <option value="ADVANCED">고급</option>
        </select>
      </div>

      {!courses ? (
        <div className="panel"><p className="note">고르는 중…</p></div>
      ) : shown.length === 0 ? (
        <div className="empty">
          <img src={SPR.sad} alt="" />
          <p>조건에 맞는 강의가 없어요. 필터를 조금 풀어보세요.</p>
        </div>
      ) : (
        shown.map((c) => (
          <div key={c.id} className="panel">
            <div className="row">
              <div style={{ flex: 1, minWidth: 220 }}>
                <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                  <span className="chip navy">{PLATFORM_LABEL[c.platform] || c.platform}</span>
                  <span className="chip gray">{c.level}</span>
                  <span className="chip low">{c.category}</span>
                </div>
                <b style={{ fontSize: 15 }}>{c.title}</b>
                <p className="note sm" style={{ marginTop: 6 }}>
                  {c.price === 0 ? '무료' : c.price.toLocaleString() + '원'} · {hours(c.duration)}
                </p>
              </div>
              <a className="btn co sm" href={c.url} target="_blank" rel="noreferrer">보러가기 →</a>
            </div>
          </div>
        ))
      )}
    </main>
  );
}
