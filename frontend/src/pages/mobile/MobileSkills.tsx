/*
 * 모바일 전용 AI 스킬 추천.
 * 데스크톱은 AI 선택 탭이 한 줄에 4개인데 폰에서는 접힌다. 2×2 격자로 바꿨다.
 * 기능은 데스크톱과 같다 — AI별 스킬 목록, 즐겨찾기 토글, 내 약점 표시, 공식 문서 링크.
 */
import { useEffect, useState } from "react";
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

// 셋을 한 화면에 쌓아 두니 폰에서는 스크롤만 길었다. 한 번에 하나만 띄운다
const TABS: [string, string][] = [
  ["list", "추천 스킬"],
  ["weak", "내 약점"],
  ["ask", "질문하기"],
  // AI 추천은 새로 받으면 이전 결과를 덮어쓴다. 별을 달아 둔 것만 여기 남는다
  ["fav", "즐겨찾기"],
];

export default function MobileSkills() {
  const { spendCredit, refundCredit, notify } = useGame();
  const [tab, setTab] = useState("list");
  const [provider, setProvider] = useState("CLAUDE");
  const [skills, setSkills] = useState(null);
  const [weakness, setWeakness] = useState([]);

  useEffect(() => { api.getAiSkills(provider).then(setSkills).catch(() => setSkills([])); }, [provider]);

  useEffect(() => {
    api.getWeaknessStats().then((ws) => setWeakness(ws.map((w) => w.category))).catch(() => {});
  }, []);

  // 별을 누르면 바로 화면에 반영하고 서버에도 저장한다
  const toggleFavorite = async (skill) => {
    setSkills((prev) => prev.map((s) => (s.id === skill.id ? { ...s, isFavorite: !s.isFavorite } : s)));
    await api.toggleSkillFavorite(skill.id);
  };

  // 즐겨찾기 — 탭에 들어올 때마다 다시 부른다. 다른 탭에서 별을 켜고 넘어오면 바로 보여야 한다
  const [favorites, setFavorites] = useState(null);
  useEffect(() => {
    if (tab !== "fav") return;
    setFavorites(null);
    api.getFavoriteSkills().then(setFavorites).catch(() => setFavorites([]));
  }, [tab]);

  // 여기서 별을 끄면 목록에서 빠진다 — 즐겨찾기 목록이니 남아 있으면 이상하다
  const unfavorite = async (skill) => {
    setFavorites((prev) => prev.filter((s) => s.id !== skill.id));
    try {
      await api.toggleSkillFavorite(skill.id);
    } catch {
      setFavorites((prev) => [...prev, skill]);
      notify("즐겨찾기를 바꾸지 못했어요");
    }
  };

  // AI에게 직접 추천받기 — 큐레이션에 없을 때 자유 입력으로 물어본다 (크레딧 1)
  const [askText, setAskText] = useState("");
  const [asking, setAsking] = useState(false);
  const [askResult, setAskResult] = useState(null);

  // 크레딧을 쓴 결과라 이것도 화면을 나갔다 오면 남아야 한다. 약점 기반과 따로 꺼낸다
  useEffect(() => {
    api.getLatestSkillRecommendation("FREE_TEXT")
      .then((r) => { if (r?.items?.length) setAskResult(r); })
      .catch(() => {});
  }, []);

  const askAi = async () => {
    if (asking || !askText.trim()) return;
    if (!spendCredit(1)) return;
    setAsking(true);
    try {
      // 응답은 약점 기반 추천과 같은 { weaknesses, items } 모양이라 아래 카드도 그대로 쓴다
      setAskResult(await api.recommendSkill(askText));
    } catch (e) {
      refundCredit(1); // 서버는 롤백으로 안 썼으니 로컬 원장도 되돌린다
      notify(e.message || "추천을 받지 못했어요"); // 실패를 조용히 삼키지 않는다
    } finally { setAsking(false); }
  };

  // 내 약점에 맞는 스킬 — 입력 없이 서버가 약점 통계를 읽어 AI별로 하나씩 골라준다 (크레딧 2)
  const [byWeak, setByWeak] = useState(null);
  const [byWeakBusy, setByWeakBusy] = useState(false);

  // 2크레딧 쓴 결과라 화면을 나갔다 와도 남아야 한다. 서버 이력에서 꺼내온다
  useEffect(() => {
    api.getLatestSkillRecommendation().then((r) => { if (r?.items?.length) setByWeak(r); }).catch(() => {});
  }, []);

  // 추천으로 만들어진 스킬도 별을 달 수 있다. 켜두면 스튜디오 후속질문 칩으로 올라온다
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
      flip(item.isFavorite); // 거절당하면 화면도 되돌린다
      notify(e.message || "즐겨찾기를 바꾸지 못했어요");
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

      {/* 데스크톱과 같은 세 갈래. 폰은 폭이 좁아 세그먼트로 */}
      <div className="mseg msk-seg">
        {TABS.map(([k, label]) => (
          <button key={k} className={tab === k ? "on" : ""} onClick={() => setTab(k)}>{label}</button>
        ))}
      </div>

      {/* ── 내 약점에 맞는 스킬 ── */}
      {tab === "weak" && (
        <>
          <button className="btn co sm full msk-byweak" onClick={askByWeakness} disabled={byWeakBusy}>
            {byWeakBusy ? "약점 훑는 중…" : "내 약점에 맞는 스킬 추천받기 (⚡2)"}
          </button>

          {byWeak ? (
            <section className="mcard msk-rec">
              <b>내 약점 기준 추천</b>
              <p className="mnote">
                {byWeak.weaknesses?.length > 0
                  ? `${byWeak.weaknesses.map(catKo).join(" · ")} 약점을 보고 골랐어요.`
                  : "약점 통계를 보고 골랐어요."}
              </p>
              <RecItems items={byWeak.items} onStar={(it) => toggleRecFavorite(setByWeak, it)} onCopy={copyPrompt} />
            </section>
          ) : (
            <p className="mnote">아직 받은 추천이 없어요. 위 버튼을 눌러보세요.</p>
          )}
        </>
      )}

      {/* ── 추천 스킬(큐레이션 목록) ── */}
      {tab === "list" && (
        <>
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
            <p>이 AI에 등록된 스킬이 아직 없어요.</p>
          </section>
        ) : (
          skills.map((s) => (
            <SkillCard key={s.id} skill={s} weakness={weakness}
              onStar={() => toggleFavorite(s)} onCopy={copyPrompt} />
          ))
        )}

        </>
      )}

      {/* ── 즐겨찾기 ── */}
      {tab === "fav" && (
        !favorites ? (
          <p className="mnote">불러오는 중…</p>
        ) : favorites.length === 0 ? (
          <section className="mcard mempty">
            <p>아직 즐겨찾기한 스킬이 없어요.<br />AI 추천은 다시 받으면 이전 결과가 사라지니 즐겨찾기로 저장해두세요!</p>
          </section>
        ) : (
          favorites.map((s) => (
            <SkillCard key={s.id} skill={s} weakness={weakness}
              onStar={() => unfavorite(s)} onCopy={copyPrompt} />
          ))
        )
      )}

      {/* ── 스킬 질문하기 ── */}
      {tab === "ask" && (
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
        {/* 약점 기반 추천과 같은 카드. 프롬프트를 복사하거나 별을 달아 스튜디오에서 꺼내 쓴다 */}
        {askResult?.items?.length > 0 && (
          <RecItems items={askResult.items} onStar={(it) => toggleRecFavorite(setAskResult, it)} onCopy={copyPrompt} />
        )}
      </section>
      )}

    </main>
  );
}

// 추천 항목 카드 — 약점 기반과 자유 입력이 같은 응답 모양이라 한 벌로 쓴다
// 스킬 카드 한 장 — 큐레이션 목록과 즐겨찾기가 같은 DTO를 쓴다(둘 다 ai_skills 행)
function SkillCard({ skill: s, weakness, onStar, onCopy }) {
  return (
    <section className="mcard msk">
      <div className="msk-head">
        <b>{s.title}</b>
        <button className={`msk-star ${s.isFavorite ? "on" : ""}`}
          onClick={onStar}
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

      {/* AI가 만들어 준 스킬만 붙여넣을 프롬프트를 들고 있다 */}
      {s.prompt && (
        <>
          <pre className="msk-rec-prompt">{s.prompt}</pre>
          <button className="btn wh sm full" onClick={() => onCopy(s.prompt)}>프롬프트 복사</button>
        </>
      )}

      {s.url && <a className="msk-doc" href={s.url} target="_blank" rel="noreferrer">공식 문서 보기 ↗</a>}
    </section>
  );
}

function RecItems({ items, onStar, onCopy }) {
  return items.map((it) => (
    <div key={it.skillId ?? it.title} className="msk-rec-item">
      <div className="msk-head">
        <span className="msk-cat">{PROVIDER_KO[it.provider] ?? it.provider}</span>
        {/* 큐레이션 스킬과 같은 별 */}
        <button className={`msk-star ${it.isFavorite ? "on" : ""}`}
          onClick={() => onStar(it)}
          aria-label={it.isFavorite ? "즐겨찾기 해제" : "즐겨찾기"}>
          {it.isFavorite ? "★" : "☆"}
        </button>
      </div>
      <b className="msk-rec-title">{it.title}</b>
      <p className="msk-desc">{it.why}</p>
      <div className="msk-how">
        <b>바로 쓰는 법</b>
        <p>{it.howTo}</p>
      </div>
      <pre className="msk-rec-prompt">{it.prompt}</pre>
      <button className="btn wh sm full" onClick={() => onCopy(it.prompt)}>프롬프트 복사</button>
    </div>
  ));
}
