/*
 * AI 스킬 추천 (LRN-005, FR-70)
 * 내가 쓰는 AI를 고르면 그 AI에서 실제로 쓸 수 있는 스킬만 걸러 보여준다.
 * 큐레이션된 데이터를 읽는 거라 AI 호출도 크레딧도 없다.
 * 즐겨찾기해 둔 스킬은 나중에 리뷰 스튜디오 후속질문에서 꺼내 쓸 예정.
 */
import { useEffect, useState } from 'react';
import * as api from '../../api/client';
import { useGame } from '../../context/GameContext';
import { PageHead, Tabs } from '../../components/ui';
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

// 셋을 한 화면에 쌓아 두니 스크롤만 길고 뭐가 뭔지 안 보였다. 한 번에 하나만 띄운다
const TABS: [string, string][] = [
  ['list', '추천 스킬'],
  ['weak', '내 약점에 맞는 스킬'],
  ['ask', '스킬 질문하기'],
];

// 폰이면 데스크톱 본체를 아예 mount하지 않는다.
// 한 컴포넌트 안에서 갈랐더니 조건부 return 위의 useEffect가 그대로 돌아 같은 API를 두 번 때렸다.
// getWeaknessStats처럼 조회할 때마다 통계를 지우고 다시 넣는 API는 두 번 겹치면 한쪽이 빈 목록을 받는다.
export default function Skills() {
  return useIsMobile() ? <MobileSkills /> : <DesktopSkills />;
}

function DesktopSkills() {
  const { spendCredit, refundCredit, notify } = useGame();
  const [tab, setTab] = useState('list');
  const [provider, setProvider] = useState('CLAUDE');
  const [skills, setSkills] = useState(null);
  const [weakness, setWeakness] = useState([]); // 내 약점 카테고리 — 관련 스킬에 표시를 남긴다

  // 실패를 안 잡으면 콘솔에만 뜨고 화면은 "불러오는 중…"에서 멈춘다
  useEffect(() => {
    api.getAiSkills(provider)
      .then(setSkills)
      .catch((e) => { setSkills([]); notify(e.message || '스킬 목록을 불러오지 못했어요'); });
  }, [provider]);

  // 약점은 목록에 "내 약점" 표시를 다는 부가 정보라 실패해도 화면은 그대로 쓴다
  useEffect(() => {
    api.getWeaknessStats().then((ws) => setWeakness(ws.map((w) => w.category))).catch(() => setWeakness([]));
  }, []);

  // 별을 누르면 바로 화면에 반영하고 서버에도 저장한다
  const toggleFavorite = async (skill) => {
    setSkills((prev) => prev.map((s) => (s.id === skill.id ? { ...s, isFavorite: !s.isFavorite } : s)));
    await api.toggleSkillFavorite(skill.id);
  };

  // AI에게 직접 추천받기 — 큐레이션에 없을 때 자유 입력으로 물어본다 (크레딧 1)
  // 응답은 약점 기반 추천과 같은 { weaknesses, items } 모양이라 아래 카드도 그대로 쓴다
  const [askText, setAskText] = useState('');
  const [asking, setAsking] = useState(false);
  const [askResult, setAskResult] = useState(null);

  // 크레딧을 쓴 결과라 이것도 화면을 나갔다 오면 남아야 한다. 약점 기반과 따로 꺼낸다
  useEffect(() => {
    api.getLatestSkillRecommendation('FREE_TEXT')
      .then((r) => { if (r?.items?.length) setAskResult(r); })
      .catch(() => {});
  }, []);

  const askAi = async () => {
    if (asking || !askText.trim()) return;
    if (!spendCredit(1)) return;
    setAsking(true);
    try {
      setAskResult(await api.recommendSkill(askText));
    } catch (e) {
      refundCredit(1); // 서버는 롤백으로 안 썼으니 로컬 원장도 되돌린다
      notify(e.message || '추천을 받지 못했어요'); // 실패를 조용히 삼키지 않는다
    } finally { setAsking(false); }
  };

  // 내 약점에 맞는 스킬 — 입력 없이 서버가 약점 통계를 읽어 AI별로 하나씩 골라준다 (크레딧 2)
  const [byWeak, setByWeak] = useState(null);
  const [byWeakBusy, setByWeakBusy] = useState(false);

  // 2크레딧을 쓴 결과라 화면을 나갔다 와도 남아야 한다. 서버에 이력이 있으니 마운트 때 꺼내온다
  useEffect(() => {
    api.getLatestSkillRecommendation().then((r) => { if (r?.items?.length) setByWeak(r); }).catch(() => {});
  }, []);

  // 추천으로 만들어진 스킬도 내 스킬이라 별을 달 수 있다. 즐겨찾기하면 스튜디오 후속질문 칩으로 올라온다.
  // 약점 기반이든 자유 입력이든 응답 모양이 같아서 setter만 갈아끼운다
  const toggleRecFavorite = async (setter, item) => {
    if (!item.skillId) return; // 옛 이력엔 skillId가 없다
    const flip = (on) => setter((prev) => ({
      ...prev,
      items: prev.items.map((x) => (x.skillId === item.skillId ? { ...x, isFavorite: on } : x)),
    }));
    flip(!item.isFavorite);
    try {
      await api.toggleSkillFavorite(item.skillId);
    } catch (e) {
      flip(item.isFavorite); // 서버가 거절했으면 화면도 되돌린다. 안 그러면 별만 켜진 채 남는다
      notify(e.message || '즐겨찾기를 바꾸지 못했어요');
    }
  };

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
      <PageHead
        badge="AI SKILLS" badgeCls="co"
        title="AI 스킬 추천"
        lead={"쓰고 계신 AI를 고르면 거기서 바로 쓸 수 있는 기능만 모아드려요.\n내 약점에 맞는 스킬에는 표시를 달아뒀습니다."}
      />

      {/* 셋을 한 줄로 쌓아 두니 스크롤이 길어 뭐가 뭔지 안 보였다. 탭으로 하나씩만 띄운다 */}
      <Tabs items={TABS} value={tab} onChange={setTab} />

      {/* ── 내 약점에 맞는 스킬 ── */}
      {tab === 'weak' && (
        <>
          <div className="panel skill-lead">
            <p className="note">
              약점 통계를 서버가 직접 읽어 Claude · ChatGPT · Gemini · Copilot 네 곳에서 하나씩 골라줍니다.
              <br />
              결과는 화면을 나갔다 와도 남아요.
            </p>
            <button className="btn co sm" onClick={askByWeakness} disabled={byWeakBusy}>
              {byWeakBusy ? '약점 훑는 중…' : '내 약점에 맞는 스킬 추천받기 (⚡2)'}
            </button>
          </div>

          {byWeak ? (
            <div className="panel skill-byweak-box">
              <b>내 약점 기준 추천</b>
              <p className="note sm" style={{ marginTop: 6 }}>
                {byWeak.weaknesses?.length > 0
                  ? `${byWeak.weaknesses.map(catKo).join(' · ')} 약점을 보고 골랐어요.`
                  : '약점 통계를 보고 골랐어요.'}
              </p>
              <RecItems items={byWeak.items} onStar={(it) => toggleRecFavorite(setByWeak, it)} onCopy={copyPrompt} />
            </div>
          ) : (
            <div className="panel"><p className="note">아직 받은 추천이 없어요. 위 버튼을 눌러보세요.</p></div>
          )}
        </>
      )}

      {/* ── 스킬 질문하기 ── */}
      {tab === 'ask' && (
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
          {/* 약점 기반 추천과 같은 카드. 프롬프트를 복사하거나 별을 달아 스튜디오에서 꺼내 쓴다 */}
          {askResult?.items?.length > 0 && (
            <RecItems items={askResult.items} onStar={(it) => toggleRecFavorite(setAskResult, it)} onCopy={copyPrompt} />
          )}
        </div>
      )}

      {/* ── 추천 스킬(큐레이션 목록) ── */}
      {tab === 'list' && (
        <>
          {/* 내가 쓰는 AI 선택. 위 탭과 같은 모양이면 어느 쪽이 상위인지 헷갈려서 알약으로 구분한다 */}
          <div className="skill-providers">
            <span className="note sm">내가 쓰는 AI</span>
            <div className="pick-grid">
              {PROVIDERS.map(([code, label]) => (
                <button key={code} className={`pick ${provider === code ? 'on' : ''}`} onClick={() => setProvider(code)}>
                  {label}
                </button>
              ))}
            </div>
          </div>

          {!skills ? (
            <div className="panel"><p className="note">불러오는 중…</p></div>
          ) : skills.length === 0 ? (
            <div className="panel">
              <p className="note">이 AI에 등록된 스킬이 아직 없어요.</p>
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

                {/* AI가 만들어 준 스킬만 붙여넣을 프롬프트를 들고 있다. 공용 큐레이션 행은 없다 */}
                {s.prompt && (
                  <div className="skill-rec-prompt">
                    <pre>{s.prompt}</pre>
                    <button className="btn wh sm" onClick={() => copyPrompt(s.prompt)}>복사</button>
                  </div>
                )}

                {s.url && (
                  <a className="link-line" href={s.url} target="_blank" rel="noreferrer"
                    style={{ display: 'inline-block', marginTop: 12, fontSize: 12.5 }}>
                    공식 문서 보기 →
                  </a>
                )}
              </div>
            ))
          )}
        </>
      )}
    </main>
  );
}

// 추천 항목 카드 — 약점 기반과 자유 입력이 같은 응답 모양이라 한 벌로 쓴다
function RecItems({ items, onStar, onCopy }) {
  return items.map((it) => (
    <div key={it.skillId ?? it.title} className="skill-rec">
      <div className="skill-rec-top">
        <span className="chip navy">{PROVIDER_KO[it.provider] ?? it.provider}</span>
        <b>{it.title}</b>
        {/* 큐레이션 스킬과 같은 별. 켜두면 스튜디오 후속질문에서 바로 꺼내 쓴다 */}
        <button className={`star ml-auto ${it.isFavorite ? 'on' : ''}`}
          onClick={() => onStar(it)}
          title={it.isFavorite ? '즐겨찾기 해제' : '즐겨찾기'}>
          {it.isFavorite ? '★' : '☆'}
        </button>
      </div>
      <p className="skill-rec-why">{it.why}</p>
      <div className="skill-how">
        <b>바로 쓰는 법</b>
        <p>{it.howTo}</p>
      </div>
      <div className="skill-rec-prompt">
        <pre>{it.prompt}</pre>
        <button className="btn wh sm" onClick={() => onCopy(it.prompt)}>복사</button>
      </div>
    </div>
  ));
}
