/*
 * AI 스킬 추천 (LRN-005, FR-70)
 * 내가 쓰는 AI를 고르면 그 AI에서 실제로 쓸 수 있는 스킬만 걸러 보여준다.
 * 큐레이션된 데이터를 읽는 거라 AI 호출도 크레딧도 없다.
 * 즐겨찾기해 둔 스킬은 나중에 리뷰 스튜디오 후속질문에서 꺼내 쓸 예정.
 */
import { useEffect, useState } from 'react';
import * as api from '../../api/client';
import { PageHead } from '../../components/ui';
import { CATEGORY_KO } from '../../data/constants';
import useIsMobile from '../../hooks/useIsMobile';
import MobileSkills from '../mobile/MobileSkills';

// 화면에 띄울 AI 목록. 값은 백엔드 ai_skills.provider와 같아야 한다
const PROVIDERS = [
  ['CLAUDE', 'Claude'],
  ['CHATGPT', 'ChatGPT'],
  ['GEMINI', 'Gemini'],
  ['COPILOT', 'GitHub Copilot'],
];

// 폰이면 데스크톱 본체를 아예 mount하지 않는다.
// 한 컴포넌트 안에서 갈랐더니 조건부 return 위의 useEffect가 그대로 돌아 같은 API를 두 번 때렸다.
// getWeaknessStats처럼 조회할 때마다 통계를 지우고 다시 넣는 API는 두 번 겹치면 한쪽이 빈 목록을 받는다.
export default function Skills() {
  return useIsMobile() ? <MobileSkills /> : <DesktopSkills />;
}

function DesktopSkills() {
  const [provider, setProvider] = useState('CLAUDE');
  const [skills, setSkills] = useState(null);
  const [weakness, setWeakness] = useState([]); // 내 약점 카테고리 — 관련 스킬에 표시를 남긴다

  useEffect(() => { api.getAiSkills(provider).then(setSkills); }, [provider]);

  useEffect(() => {
    api.getWeaknessStats().then((ws) => setWeakness(ws.map((w) => w.category)));
  }, []);

  // 별을 누르면 바로 화면에 반영하고 서버에도 저장한다
  const toggleFavorite = async (skill) => {
    setSkills((prev) => prev.map((s) => (s.id === skill.id ? { ...s, isFavorite: !s.isFavorite } : s)));
    await api.toggleSkillFavorite(skill.id);
  };


  return (
    <main className="app-main">
      <PageHead
        badge="AI SKILLS" badgeCls="co"
        title="AI 스킬 추천"
        lead={"쓰고 계신 AI를 고르면 거기서 바로 쓸 수 있는 기능만 모아드려요.\n내 약점에 맞는 스킬에는 표시를 달아뒀습니다."}
      />

      {/* 내가 쓰는 AI 선택 — 여기서 고른 것에 맞춰 아래 목록이 바뀐다 */}
      <div className="tabs">
        {PROVIDERS.map(([code, label]) => (
          <button key={code} className={provider === code ? 'on' : ''} onClick={() => setProvider(code)}>
            {label}
          </button>
        ))}
      </div>

      {!skills ? (
        <div className="panel"><p className="note">불러오는 중…</p></div>
      ) : skills.length === 0 ? (
        <div className="panel"><p className="note">이 AI에 등록된 스킬이 아직 없어요.</p></div>
      ) : (
        skills.map((s) => (
          <div key={s.id} className="panel skill-card">
            <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <b style={{ fontSize: 15 }}>{s.title}</b>
                <div className="row" style={{ gap: 6, marginTop: 8 }}>
                  <span className="chip navy">{CATEGORY_KO[s.category] ?? s.category}</span>
                  {/* 내 약점과 겹치면 눈에 띄게 — 이게 이 화면의 핵심이다 */}
                  {weakness.includes(s.category) && <span className="chip hi">내 약점</span>}
                </div>
              </div>
              <button
                className={`star ${s.isFavorite ? 'on' : ''}`}
                onClick={() => toggleFavorite(s)}
                title={s.isFavorite ? '즐겨찾기 해제' : '즐겨찾기'}>
                {s.isFavorite ? '★' : '☆'}
              </button>
            </div>

            <p style={{ fontSize: 13.5, lineHeight: 2, marginTop: 12 }}>{s.description}</p>

            <div className="skill-how">
              <b>이렇게 씁니다</b>
              <p>{s.howTo}</p>
            </div>

            {s.url && (
              <a className="link-line" href={s.url} target="_blank" rel="noreferrer"
                style={{ display: 'inline-block', marginTop: 12, fontSize: 12.5 }}>
                공식 문서 보기 →
              </a>
            )}
          </div>
        ))
      )}
    </main>
  );
}
