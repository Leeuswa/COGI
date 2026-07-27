/*
 * 강의 추천 (LRN-003~004) — 딥링크 방식
 * 약점 통계를 백엔드가 인프런·Udemy 검색 딥링크로 조립해 내려준다.
 * (GET /api/users/me/course-recommendations) 프론트는 그대로 렌더만 한다.
 */
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/client';
import { PageHead } from '../../components/ui';
import { SPR } from '../../assets/sprites';

export default function Courses() {
  const [list, setList] = useState(null);

  useEffect(() => { api.getCourseRecommendations().then(setList); }, []);

  return (
    <main className="app-main">
      <PageHead
        badge="COURSES"
        title="약점 맞춤 강의 추천"
        lead="약점별로 인프런 · Udemy 검색결과를 바로 열어드려요."
      />

      {!list ? (
        <div className="panel"><p className="note">불러오는 중…</p></div>
      ) : list.length === 0 ? (
        <div className="empty">
          <img src={SPR.sad} alt="" />
          <p>아직 약점 데이터가 없어요.<br />리뷰를 몇 번 받으면 여기서 맞춤 강의를 추천해드려요.</p>
          <div style={{ marginTop: 16 }}>
            <Link className="btn co sm" to="/app/weakness">내 약점 보기 →</Link>
          </div>
        </div>
      ) : (
      list.map((c, i) => (
  <div key={i} className="panel">
    <div style={{ display: "flex", justifyContent: "space-between", gap: 20 }}>
      <div style={{ flex: 1 }}>
        <span className="chip low">{c.platform}</span>

        <h3 style={{ marginTop: 10 }}>
          {c.title}
        </h3>

        <p className="note sm" style={{ marginTop: 8 }}>
          {c.description}
        </p>
      </div>

      <div style={{ display: "flex", alignItems: "center" }}>
        <a
          className="btn co sm"
          href={c.url}
          target="_blank"
          rel="noreferrer"
        >
          강의 보기 →
        </a>
      </div>
    </div>
  </div>
))
      )}
    </main>
  );
}
