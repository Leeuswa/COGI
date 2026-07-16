/*
 * 관심기술 선택기 (온보딩 · 마이페이지 공용)
 * 백엔드/프론트엔드/DB/클라우드 분야 탭 — 탭을 오가며 골라도 선택이 그대로 유지되어
 * 서로 다른 분야를 섞어 담을 수 있다. 선택 상태는 부모가 플랫 배열 하나로 들고 있다.
 */
import { useState } from 'react';
import { INTEREST_GROUPS } from '../data/constants';

export default function InterestPicker({ value, onToggle }) {
  const groups = Object.keys(INTEREST_GROUPS);
  const [tab, setTab] = useState(groups[0]);
  const countIn = (g) => INTEREST_GROUPS[g].filter((t) => value.includes(t)).length;

  return (
    <div>
      <div className="itabs" role="tablist">
        {groups.map((g) => (
          <button key={g} role="tab" aria-selected={tab === g}
            className={tab === g ? 'on' : ''} onClick={() => setTab(g)}>
            {g}{countIn(g) > 0 && <span className="pick-cnt">{countIn(g)}</span>}
          </button>
        ))}
      </div>
      <div className="pick-grid">
        {INTEREST_GROUPS[tab].map((t) => (
          <button key={t} className={`pick ${value.includes(t) ? 'on' : ''}`} onClick={() => onToggle(t)}>{t}</button>
        ))}
      </div>
      <p className="note sm" style={{ marginTop: 12 }}>
        {value.length > 0
          ? `선택한 기술 ${value.length}개 — ${value.join(' · ')}`
          : '위는 분야 탭이고, 아래에서 기술을 눌러 담으세요. 분야를 오가며 섞어도 그대로 유지돼요.'}
      </p>
    </div>
  );
}
