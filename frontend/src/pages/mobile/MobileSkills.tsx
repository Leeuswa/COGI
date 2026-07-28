/*
 * 모바일 전용 AI 스킬 추천.
 * 데스크톱은 AI 선택 탭이 한 줄에 4개인데 폰에서는 접힌다. 2×2 격자로 바꿨다.
 * 기능은 데스크톱과 같다 — AI별 스킬 목록, 즐겨찾기 토글, 내 약점 표시, 공식 문서 링크.
 */
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import * as api from "../../api/client";
import { useGame } from "../../context/GameContext";
import { CATEGORY_KO, catKo } from "../../data/constants";
import "../../styles/mobile/skills.css";

const PROVIDERS = [
  ["CLAUDE", "Claude"],
  ["CHATGPT", "ChatGPT"],
  ["GEMINI", "Gemini"],
  ["COPILOT", "Copilot"],
];

// 추천 결과 배지에 쓸 이름
const PROVIDER_KO = Object.fromEntries(PROVIDERS);

export default function MobileSkills() {
  const { spendCredit, refundCredit, notify } = useGame();
  const [provider, setProvider] = useState("CLAUDE");
  const [skills, setSkills] = useState(null);
  const [weakness, setWeakness] = useState([]);
  const [searchParams, setSearchParams] = useSearchParams();
  const category = searchParams.get("category"); // 약점 화면에서 넘어온 필터 — 탭을 바꿔도 유지된다

  useEffect(() => { api.getAiSkills(provider, category).then(setSkills).catch(() => setSkills([])); }, [provider, category]);

  useEffect(() => {
    api.getWeaknessStats().then((ws) => setWeakness(ws.map((w) => w.category))).catch(() => {});
  }, []);

  // 별을 누르면 바로 화면에 반영하고 서버에도 저장한다
  const toggleFavorite = async (skill) => {
    setSkills((prev) => prev.map((s) => (s.id === skill.id ? { ...s, isFavorite: !s.isFavorite } : s)));
    await api.toggleSkillFavorite(skill.id);
  };

  // AI에게 직접 추천받기 — 큐레이션에 없을 때 자유 입력으로 물어본다 (크레딧 1)
  const [askText, setAskText] = useState(category ? `${catKo(category)} 약점을 줄이고 싶어요` : "");
  const [asking, setAsking] = useState(false);
  const [askResult, setAskResult] = useState(null);
  // 필터가 바뀌면(다른 약점에서 새로 들어옴) 입력 기본값도 그 카테고리로 다시 채운다
  useEffect(() => { setAskText(category ? `${catKo(category)} 약점을 줄이고 싶어요` : ""); }, [category]);

  const askAi = async () => {
    if (asking || !askText.trim()) return;
    if (!spendCredit(1)) return;
    setAsking(true);
    try {
      const res = await api.recommendSkill(askText);
      setAskResult(res.result);
    } catch (e) {
      refundCredit(1); // 서버는 롤백으로 안 썼으니 로컬 원장도 되돌린다
      notify(e.message || "추천을 받지 못했어요"); // 실패를 조용히 삼키지 않는다
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
      notify(e.message || "추천을 받지 못했어요");
    } finally { setByWeakBusy(false); }
  };

  // 프롬프트를 눌러 담기 — 붙여넣기만 하면 되게
  const copyPrompt = async (text) => {
    await navigator.clipboard.writeText(text);
    notify("복사했어요. 쓰는 AI에 그대로 붙여넣으세요");
  };

  return (
    <main className="mapp">
      <p className="mlead">쓰고 계신 AI를 고르면 바로 쓸 수 있는 기능만 모아드려요.</p>

      {/* 이 화면에서 제일 강한 행동이라 맨 위. 폰은 폭이 좁아 한 줄 전체를 쓴다 */}
      <button className="btn co sm full msk-byweak" onClick={askByWeakness} disabled={byWeakBusy}>
        {byWeakBusy ? "약점 훑는 중…" : "내 약점에 맞는 스킬 추천받기 (⚡2)"}
      </button>

      {/* 약점 화면에서 카테고리를 달고 넘어온 경우 — 지금 뭘 보고 있는지 알려주고 풀 수 있게 */}
      {category && (
        <section className="msk-filter">
          <p>{catKo(category)} 약점에 맞는 스킬</p>
          <button className="btn wh sm" onClick={() => setSearchParams({}, { replace: true })}>전체 보기</button>
        </section>
      )}

      {/* 약점 기반 추천 결과 — AI 네 곳을 한 번에. 프롬프트는 눌러서 바로 복사한다 */}
      {byWeak && (
        <section className="mcard msk-rec">
          <b>내 약점 기준 추천</b>
          <p className="mnote">
            {byWeak.weaknesses?.length > 0
              ? `${byWeak.weaknesses.map(catKo).join(" · ")} 약점을 보고 골랐어요.`
              : "약점 통계를 보고 골랐어요."}
          </p>
          {byWeak.items?.map((it) => (
            <div key={it.provider} className="msk-rec-item">
              <span className="msk-cat">{PROVIDER_KO[it.provider] ?? it.provider}</span>
              <b className="msk-rec-title">{it.title}</b>
              <p className="msk-desc">{it.why}</p>
              <div className="msk-how">
                <b>바로 쓰는 법</b>
                <p>{it.howTo}</p>
              </div>
              <pre className="msk-rec-prompt">{it.prompt}</pre>
              <button className="btn wh sm full" onClick={() => copyPrompt(it.prompt)}>프롬프트 복사</button>
            </div>
          ))}
        </section>
      )}

      {/* AI 선택 — 폰에서는 2×2 */}
      <div className="msk-tabs">
        {PROVIDERS.map(([code, label]) => (
          <button key={code} className={provider === code ? "on" : ""} onClick={() => setProvider(code)}>
            {label}
          </button>
        ))}
      </div>

      {!skills ? (
        <p className="mnote">불러오는 중…</p>
      ) : skills.length === 0 ? (
        <section className="mcard mempty">
          <p>{category ? "이 약점에 맞는 큐레이션 스킬이 아직 없어요." : "이 AI에 등록된 스킬이 아직 없어요."}</p>
        </section>
      ) : (
        skills.map((s) => (
          <section key={s.id} className="mcard msk">
            <div className="msk-head">
              <b>{s.title}</b>
              <button className={`msk-star ${s.isFavorite ? "on" : ""}`}
                onClick={() => toggleFavorite(s)}
                aria-label={s.isFavorite ? "즐겨찾기 해제" : "즐겨찾기"}>
                {s.isFavorite ? "★" : "☆"}
              </button>
            </div>

            <div className="msk-tags">
              <span className="msk-cat">{CATEGORY_KO[s.category] ?? s.category}</span>
              {/* 내 약점과 겹치면 눈에 띄게 — 이게 이 화면의 핵심이다 */}
              {weakness.includes(s.category) && <span className="msk-mine">내 약점</span>}
            </div>

            <p className="msk-desc">{s.description}</p>

            <div className="msk-how">
              <b>이렇게 씁니다</b>
              <p>{s.howTo}</p>
            </div>

            {s.url && (
              <a className="msk-doc" href={s.url} target="_blank" rel="noreferrer">공식 문서 보기 ↗</a>
            )}
          </section>
        ))
      )}

      {/* 큐레이션 목록을 다 훑고도 없을 때 물어보는 자리라 맨 아래 (크레딧 1) */}
      <section className="mcard msk-ask">
        <b>찾는 게 없나요?</b>
        <p className="mnote">약점이나 원하는 걸 자유롭게 적어주세요.</p>
        <textarea
          rows={3}
          value={askText}
          onChange={(e) => setAskText(e.target.value)}
          placeholder="예: null 체크를 자꾸 빼먹어요"
        />
        <button className="btn co sm full" onClick={askAi} disabled={asking || !askText.trim()}>
          {asking ? "추천 받는 중…" : "AI에게 추천받기 (⚡1)"}
        </button>
        {askResult && <p className="msk-ask-result">{askResult}</p>}
      </section>

    </main>
  );
}
