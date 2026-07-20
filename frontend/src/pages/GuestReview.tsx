/*
 * 비로그인 즉시 체험 (LOC-001, FR-50~52)
 * - 코드 붙여넣기 → 즉시 리뷰. 회원가입 없이 서비스 감을 잡게 하는 진입로.
 * - 3회 제한: 명세는 쿠키 기반이지만 목 모드에선 localStorage 로 흉내.
 *   실서버 전환 시 서버가 쿠키로 세고 429를 주므로 이 카운트는 안내용으로만 남는다.
 * - 3회 소진 후엔 입력을 잠그고 가입 유도.
 */
import { useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../api/client';
import { PageHead, SevChip } from '../components/ui';
import { catKo } from '../data/constants';
import { SPR } from '../assets/sprites';

const KEY = 'cogi-guest-used';
const LIMIT = 3;

export default function GuestReview() {
  const [code, setCode] = useState('');
  const [language, setLanguage] = useState('TypeScript');
  const [busy, setBusy] = useState(false);
  const [level, setLevel] = useState('BEGINNER'); // 수준에 따라 설명 깊이가 달라진다 (ADM-004 지침)
  const [issues, setIssues] = useState(null);
  const [used, setUsed] = useState(() => Number(localStorage.getItem(KEY) || 0));

  const left = LIMIT - used;

  const run = async () => {
    if (!code.trim() || busy || left <= 0) return;
    setBusy(true);
    try {
      const res = await api.guestReview(code, language, level);
      setIssues(res.comments); // 백엔드 응답 필드명은 comments
      // 가입하면 이 리뷰를 계정으로 가져갈 수 있게 식별자를 남긴다 (API-039-1, 24시간 뒤 서버가 폐기)
      // guestToken은 HttpOnly 쿠키(guest_token)로 자동 전송되므로 reviewId만 남긴다
      if (res.reviewId) localStorage.setItem('cogi-guest-review', JSON.stringify({ reviewId: res.reviewId }));
      const next = used + 1;
      setUsed(next);
      localStorage.setItem(KEY, String(next));
    } catch (e) {
      // 서버가 403(체험 3회 소진) 등으로 거절한 경우 — 조용히 삼키지 않고 안내
      if (e?.status === 403) {
        setUsed(LIMIT); // 입력 잠그고 가입 유도 화면으로 전환
        localStorage.setItem(KEY, String(LIMIT));
      } else {
        alert('리뷰 요청에 실패했어요. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="app-main">
      <PageHead
        badge="GUEST MODE" badgeCls="co"
        title="가입 없이 바로 리뷰 받아보기"
        lead={`코드를 붙여넣으면 코기가 바로 봐드려요.\n무료 체험 ${LIMIT}회 중 ${Math.max(left, 0)}회 남았습니다.`}
      />

      {/* 내 수준 고르기 — 같은 지적도 수준에 맞춰 설명이 달라진다 */}
      <div className="panel" style={{ marginBottom: 18 }}>
        <div className="row" style={{ flexWrap: 'wrap' }}>
          <b style={{ fontSize: 14 }}>어느 정도로 설명해드릴까요?</b>
          {[['BEGINNER', '초급', '용어부터 차근차근'], ['INTERMEDIATE', '중급', '핵심과 대안 위주'], ['ADVANCED', '고급', '설계 트레이드오프까지']]
            .map(([v, ko, d]) => (
              <button key={v} className={`pick ${level === v ? 'on' : ''}`} title={d}
                onClick={() => setLevel(v)}>{ko}</button>
            ))}
          <span className="note sm">가입하면 프로필에 저장돼 매번 안 골라도 돼요</span>
        </div>
      </div>

      {left > 0 ? (
        <div className="panel">
          <div className="form">
            <div>
              <label>언어</label>
              <select value={language} onChange={(e) => setLanguage(e.target.value)}>
                {['TypeScript', 'JavaScript', 'Java', 'Python', 'Kotlin', 'Go'].map((l) => (
                  <option key={l}>{l}</option>
                ))}
              </select>
            </div>
            <div>
              <label>코드 붙여넣기</label>
              <textarea
                value={code}
                onChange={(e) => setCode(e.target.value)}
                placeholder={'function greet(user) {\n  return `hi, ${user.name}`;\n}'}
                spellCheck={false}
              />
            </div>
            <button className="btn co" onClick={run} disabled={busy || !code.trim()}>
              {busy ? '코기가 읽는 중…' : `리뷰 요청 (${left}회 남음)`}
            </button>
          </div>
        </div>
      ) : (
        // 3회 다 씀 → 여기서부터는 가입 유도 (FR-52)
        <div className="empty">
          <img src={SPR.sad} alt="" />
          <p>
            체험 3회를 모두 사용했어요.<br />
            가입하면 <b>매일 20회 무료</b> + 약점 추적 + 코기 다마고치가 열립니다.
          </p>
          <div style={{ marginTop: 18, display: 'flex', gap: 12, justifyContent: 'center' }}>
            <Link className="btn co" to="/signup">무료로 시작하기</Link>
            <Link className="btn wh" to="/login">로그인</Link>
          </div>
        </div>
      )}

      {issues && (
        <div className="panel">
          <h3>리뷰 결과</h3>
          {issues.map((it, i) => (
            <div key={i} style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <SevChip sev={it.severity} />
                <span className="chip navy">{catKo(it.category)}</span>
                <span className="mono note sm">
                  {it.filePath}:{it.lineNumber}
                </span>
              </div>
              <p style={{ fontSize: 13.5, lineHeight: 1.9 }}>{it.description}</p>
            </div>
          ))}
          <p className="note sm mt18">
            가입하면 이 이슈가 약점 통계로 쌓이고, 3회 반복 시 학습카드가 만들어집니다.{' '}
            <Link to="/signup" className="link-line">
              지금 가입하기 →
            </Link>
          </p>
        </div>
      )}
    </main>
  );
}
