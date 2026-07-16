/*
 * AI 스킬 추천 (LRN-005, API-049, FR-70)
 * 약점이나 "이런 걸 하고 싶다"를 자연어로 던지면 AI가 도구/스킬 조합을 추천.
 * AI 호출이라 크레딧 1개. 약점 카테고리를 원클릭 프리셋으로도 넣을 수 있게 했다.
 */
import { useEffect, useState } from 'react';
import * as api from '../../api/client';
import { useGame } from '../../context/GameContext';
import { PageHead } from '../../components/ui';

export default function Skills() {
  const { spendCredit } = useGame();
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [presets, setPresets] = useState([]);

  // 내 약점을 프리셋 버튼으로 — 타이핑 귀찮은 사람용
  useEffect(() => {
    api.getWeaknessStats().then((ws) => setPresets(ws.map((w) => `${w.category} (${w.language})`)));
  }, []);

  const run = async () => {
    if (!input.trim() || busy) return;
    if (!spendCredit(1)) return;
    setBusy(true);
    try {
      const res = await api.recommendSkill(input);
      setResult(res.result);
    } finally { setBusy(false); }
  };

  return (
    <main className="app-main">
      <PageHead
        badge="AI SKILLS" badgeCls="co"
        title="AI 스킬 추천"
        lead={"배우고 싶은 것이나 지금 약점을 적어보세요.\n코기가 어울리는 도구와 스킬 조합을 추천해드려요. (⚡1)"}
      />

      <div className="panel">
        {presets.length > 0 && (
          <div className="pick-grid" style={{ marginBottom: 16 }}>
            {presets.map((p) => (
              <button key={p} className="pick" onClick={() => setInput(p + ' 약점을 보완할 스킬 추천해줘')}>
                🎯 {p}
              </button>
            ))}
          </div>
        )}
        <div className="form">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="예: null 안전성 실수를 PR 전에 미리 잡고 싶어요"
            style={{ minHeight: 90 }}
          />
          <button className="btn co" onClick={run} disabled={busy || !input.trim()}>
            {busy ? '코기가 고민 중…' : '추천 받기 (⚡1)'}
          </button>
        </div>
      </div>

      {result && (
        <div className="panel">
          <h3>🐕 코기의 추천</h3>
          <p style={{ whiteSpace: 'pre-line', fontSize: 13.5, lineHeight: 2.1 }}>{result}</p>
        </div>
      )}
    </main>
  );
}
