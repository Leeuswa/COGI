/*
 * 모바일 전용 AI 스킬 추천.
 * 데스크톱은 AI 선택 탭이 한 줄에 4개인데 폰에서는 접힌다. 2×2 격자로 바꿨다.
 * 기능은 데스크톱과 같다 — AI별 스킬 목록, 즐겨찾기 토글, 내 약점 표시, 공식 문서 링크.
 */
import { useEffect, useState } from "react";
import * as api from "../../api/client";
import { CATEGORY_KO } from "../../data/constants";
import "../../styles/mobile/skills.css";

const PROVIDERS = [
  ["CLAUDE", "Claude"],
  ["CHATGPT", "ChatGPT"],
  ["GEMINI", "Gemini"],
  ["COPILOT", "Copilot"],
];

export default function MobileSkills() {
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

  return (
    <main className="mapp">
      <p className="mlead">쓰고 계신 AI를 고르면 바로 쓸 수 있는 기능만 모아드려요.</p>

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
        <section className="mcard mempty"><p>이 AI에 등록된 스킬이 아직 없어요.</p></section>
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
    </main>
  );
}
