/*
 * AI 스킬 추천 (LRN-005, FR-70)
 * 내가 쓰는 AI를 고르면 그 AI에서 실제로 쓸 수 있는 스킬만 걸러 보여준다.
 * 큐레이션된 데이터를 읽는 거라 AI 호출도 크레딧도 없다.
 * 즐겨찾기해 둔 스킬은 나중에 리뷰 스튜디오 후속질문에서 꺼내 쓸 예정.
 */
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import * as api from '../../api/client';
import { useGame } from '../../context/GameContext';
import { PageHead } from '../../components/ui';
import { CATEGORY_KO, catKo } from '../../data/constants';
import useIsMobile from '../../hooks/useIsMobile';
import MobileSkills from '../mobile/MobileSkills';

// 화면에 띄울 AI 목록. 값은 백엔드 ai_skills.provider와 같아야 한다
const PROVIDERS = [
  ['CLAUDE', 'Claude'],
  ['CHATGPT', 'ChatGPT'],
  ['GEMINI', 'Gemini'],
  ['COPILOT', 'GitHub Copilot'],
];

// 추천 결과 배지에 쓸 이름. 위 목록과 같은 값이라 여기서 한 번 더 뒤진다
const PROVIDER_KO = Object.fromEntries(PROVIDERS);

// 폰이면 데스크톱 본체를 아예 mount하지 않는다.
// 한 컴포넌트 안에서 갈랐더니 조건부 return 위의 useEffect가 그대로 돌아 같은 API를 두 번 때렸다.
// getWeaknessStats처럼 조회할 때마다 통계를 지우고 다시 넣는 API는 두 번 겹치면 한쪽이 빈 목록을 받는다.
export default function Skills() {
  return useIsMobile() ? <MobileSkills /> : <DesktopSkills />;
}

function DesktopSkills() {
  const { spendCredit, refundCredit, notify } = useGame();
  const [provider, setProvider] = useState('CLAUDE');
  const [skills, setSkills] = useState(null);
  const [weakness, setWeakness] = useState([]); // 내 약점 카테고리 — 관련 스킬에 표시를 남긴다
  const [searchParams, setSearchParams] = useSearchParams();
  const category = searchParams.get('category'); // 약점 화면에서 넘어온 필터 — 탭을 바꿔도 유지된다

  useEffect(() => { api.getAiSkills(provider, category).then(setSkills); }, [provider, category]);

  useEffect(() => {
    api.getWeaknessStats().then((ws) => setWeakness(ws.map((w) => w.category)));
  }, []);

  // 별을 누르면 바로 화면에 반영하고 서버에도 저장한다
  const toggleFavorite = async (skill) => {
    setSkills((prev) => prev.map((s) => (s.id === skill.id ? { ...s, isFavorite: !s.isFavorite } : s)));
    await api.toggleSkillFavorite(skill.id);
  };

  // AI에게 직접 추천받기 — 큐레이션에 없을 때 자유 입력으로 물어본다 (크레딧 1)
  const [askText, setAskText] = useState(category ? `${catKo(category)} 약점을 줄이고 싶어요` : '');
  const [asking, setAsking] = useState(false);
  const [askResult, setAskResult] = useState(null);
  // 필터가 바뀌면(다른 약점에서 새로 들어옴) 입력 기본값도 그 카테고리로 다시 채운다
  useEffect(() => { setAskText(category ? `${catKo(category)} 약점을 줄이고 싶어요` : ''); }, [category]);

  const askAi = async () => {
    if (asking || !askText.trim()) return;
    if (!spendCredit(1)) return;
    setAsking(true);
    try {
      const res = await api.recommendSkill(askText);
      setAskResult(res.result);
    } catch (e) {
      refundCredit(1); // 서버는 롤백으로 안 썼으니 로컬 원장도 되돌린다
      notify(e.message || '추천을 받지 못했어요'); // 실패를 조용히 삼키지 않는다
    } finally { setAsking(false); }
  };

  // 내 약점에 맞는 스킬 — 입력 없이 서버가 약점 통계를 읽어 AI별로 하나씩 골라준다 (크레딧 2)
  const [byWeak, setByWeak] = useState(null);
  const [byWeakBusy, setByWeakBusy] = useState(false);

  const askByWeakness = async () => {
    if (byWeakBusy) return;
    if (!spendCredit(2)) return; // 약점 전체를 훑고 AI 4곳을 한 번에 뽑아서 2
    setByWeakBusy(true);
    try {
      setByWeak(await api.recommendSkillByWeakness());
    } catch (e) {
      refundCredit(2); // 약점이 없거나 AI가 실패하면 서버도 안 쓴다
      notify(e.message || '추천을 받지 못했어요');
    } finally { setByWeakBusy(false); }
  };

  // 프롬프트를 눌러 담기 — 붙여넣기만 하면 되게
  const copyPrompt = async (text) => {
    await navigator.clipboard.writeText(text);
    notify('복사했어요. 쓰는 AI에 그대로 붙여넣으세요');
  };


  return (
    <main className="app-main">
      {/* 제목 오른쪽 끝에 약점 기반 추천 버튼 — 이 화면에서 제일 강한 행동이라 맨 위에 둔다 */}
      <div className="skill-head">
        <PageHead
          badge="AI SKILLS" badgeCls="co"
          title="AI 스킬 추천"
          lead={"쓰고 계신 AI를 고르면 거기서 바로 쓸 수 있는 기능만 모아드려요.\n내 약점에 맞는 스킬에는 표시를 달아뒀습니다."}
        />
        <button className="btn co sm skill-byweak" onClick={askByWeakness} disabled={byWeakBusy}>
          {byWeakBusy ? '약점 훑는 중…' : '내 약점에 맞는 스킬 추천받기 (⚡2)'}
        </button>
      </div>

      {/* 약점 화면에서 카테고리를 달고 넘어온 경우 — 지금 뭘 보고 있는지 알려주고 풀 수 있게 */}
      {category && (
        <div className="filter-bar">
          <p className="note sm" style={{ margin: 0 }}>{catKo(category)} 약점에 맞는 스킬</p>
          <button className="btn wh sm" onClick={() => setSearchParams({}, { replace: true })}>전체 보기</button>
        </div>
      )}

      {/* 약점 기반 추천 결과 — AI 네 곳을 한 번에. 프롬프트는 눌러서 바로 복사한다 */}
      {byWeak && (
        <div className="panel skill-byweak-box">
          <b>내 약점 기준 추천</b>
          <p className="note sm" style={{ marginTop: 6 }}>
            {byWeak.weaknesses?.length > 0
              ? `${byWeak.weaknesses.map(catKo).join(' · ')} 약점을 보고 골랐어요.`
              : '약점 통계를 보고 골랐어요.'}
          </p>
          {byWeak.items?.map((it) => (
            <div key={it.provider} className="skill-rec">
              <div className="skill-rec-top">
                <span className="chip navy">{PROVIDER_KO[it.provider] ?? it.provider}</span>
                <b>{it.title}</b>
              </div>
              <p className="skill-rec-why">{it.why}</p>
              <div className="skill-how">
                <b>바로 쓰는 법</b>
                <p>{it.howTo}</p>
              </div>
              <div className="skill-rec-prompt">
                <pre>{it.prompt}</pre>
                <button className="btn wh sm" onClick={() => copyPrompt(it.prompt)}>복사</button>
              </div>
            </div>
          ))}
        </div>
      )}

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
        <div className="panel">
          <p className="note">{category ? '이 약점에 맞는 큐레이션 스킬이 아직 없어요.' : '이 AI에 등록된 스킬이 아직 없어요.'}</p>
        </div>
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

      {/* 큐레이션 목록을 다 훑고도 없을 때 물어보는 자리라 맨 아래 (크레딧 1) */}
      <div className="panel skill-ask">
        <b>찾는 게 없나요?</b>
        <p className="note sm" style={{ marginTop: 6 }}>약점이나 원하는 걸 자유롭게 적어주세요.</p>
        <textarea
          className="skill-ask-input"
          rows={3}
          value={askText}
          onChange={(e) => setAskText(e.target.value)}
          placeholder="예: null 체크를 자꾸 빼먹어요"
        />
        <div className="skill-ask-foot">
          <button className="btn co sm" onClick={askAi} disabled={asking || !askText.trim()}>
            {asking ? '추천 받는 중…' : 'AI에게 추천받기 (⚡1)'}
          </button>
        </div>
        {askResult && <p className="skill-ask-result">{askResult}</p>}
      </div>
    </main>
  );
}
