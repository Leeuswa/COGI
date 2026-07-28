/*
 * 모바일 전용 강의 추천.
 * 데스크톱은 [내용 | 버튼] 가로 배치라 폰에서 버튼이 밀린다. 위아래로 나누고 카드 전체를 링크로 만든다.
 * 기능은 데스크톱과 같다 — 약점별 인프런·Udemy 검색 딥링크를 새 탭으로 연다.
 */
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import * as api from "../../api/client";
import "../../styles/mobile/courses.css";

export default function MobileCourses() {
  const [list, setList] = useState(null);
  // 빈 목록과 호출 실패는 화면이 똑같아서 "왜 안 뜨는지" 알 수가 없었다. 갈라서 보여준다
  const [failed, setFailed] = useState(false);

  useEffect(() => { api.getCourseRecommendations().then(setList).catch(() => setFailed(true)); }, []);

  if (failed) {
    return (
      <main className="mapp">
        <section className="mcard mempty">
          <p>강의를 불러오지 못했어요.<br />잠시 뒤 다시 열어봐 주세요.</p>
        </section>
      </main>
    );
  }

  if (!list) return <main className="mapp"><p className="mnote">불러오는 중…</p></main>;

  if (list.length === 0) {
    return (
      <main className="mapp">
        <section className="mcard mempty">
          <p>
            아직 추천할 강의가 없어요.<br />
            같은 지적이 3번 쌓여 약점이 되면 그에 맞는 강의를 골라드려요.
          </p>
          <Link className="btn co sm full" to="/app/weakness">약점 통계로</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="mapp">
      <p className="mlead">약점별로 인프런·Udemy 검색 결과를 바로 열어드려요.</p>

      <ul className="mcourse">
        {list.map((c, i) => (
          <li key={i}>
            {/* 카드 전체가 링크 — 폰에서는 작은 버튼보다 넓은 터치 영역이 낫다 */}
            <a href={c.url} target="_blank" rel="noreferrer">
              <div className="mco-top">
                <span className={`mco-plat ${String(c.platform).toLowerCase()}`}>{c.platform}</span>
                <span className="mco-go">↗</span>
              </div>
              <b className="mco-title">{c.title}</b>
              {c.description && <p className="mco-desc">{c.description}</p>}
            </a>
          </li>
        ))}
      </ul>
    </main>
  );
}
