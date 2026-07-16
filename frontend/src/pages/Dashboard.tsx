/*
 * 대시보드 — 로그인 후 홈.
 * 왼쪽엔 코기 다마고치(진짜 게임), 오른쪽엔 오늘의 상태 요약:
 *   - streak + 최근 4주 제출 달력 점 (RET-001)
 *   - 일일 크레딧 게이지 (SYS-003, 90% 넘으면 빨간색)
 *   - 약점 요약 (LRN-001) + 바로가기
 * 다마고치가 메인인 이유: 리텐션 장치가 곧 서비스 정체성이라서.
 */
import { Fragment, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useGame } from '../context/GameContext';
import { InfoSections } from './Landing';
import CorgiDevice from '../components/CorgiDevice';
import { PageHead } from '../components/ui';

export default function Dashboard() {
  const { user } = useAuth();
  const { S, creditLimit, syncServer, checkIn } = useGame();

  // 서버가 집계한 크레딧(API-055)·스트릭(API-051)으로 로컬 게임 상태를 보정.
  // 목 모드에선 로컬과 같은 값이 와서 변화가 없고, 실서버에선 기기 간 불일치가 여기서 맞춰진다
  useEffect(() => {
    Promise.all([api.getCreditUsage(), api.getRetentionStatus()])
      .then(([c, r]) => syncServer(c, r)).catch(() => {});
    checkIn(); // 매일 첫 방문에 출석 코인 +10 (하루 1회)
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const [weakness, setWeakness] = useState([]);

  useEffect(() => { api.getWeaknessStats().then(setWeakness); }, []);

  // 최근 28일 달력 점. submitDays 에 있는 날만 초록
  const days = [...Array(28)].map((_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - (27 - i));
    return d.toISOString().slice(0, 10);
  });

  const pct = Math.min(100, Math.round((S.creditUsed / creditLimit) * 100));

  return (
    <>
    <main className="app-main">
      <PageHead
        badge="DASHBOARD" badgeCls="co"
        title={`안녕하세요, ${user.name || user.email?.split('@')[0] || '코기'}님`}
        lead={"오늘도 코기 밥 주고 가세요.\n퀴즈 한 번 제출이면 연속 학습일이 채워집니다."}
      />

      <div className="panel-grid c2" style={{ alignItems: 'start' }}>
        {/* 코기 다마고치 본체 */}
        <CorgiDevice />

        <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
          {/* 연속 학습 (FR-73~74: 제출 기준, 정답 무관) — 최근 14일을 사슬로. 이어지면 불이 붙는다 */}
          <div className="panel">
            <div className="streak-head">
              <span className="flame">🔥</span>
              <span className="num">{S.streak}</span>
              <span className="unit">일 연속</span>
              <span className="note sm">퀴즈를 <b>제출만 해도</b> 이어져요 — 정답이 아니어도 OK</span>
            </div>
            <div className="chain" style={{ marginTop: 16 }}>
              {days.slice(-14).map((d, i, arr) => {
                const hit = S.submitDays.includes(d);
                const prevHit = i > 0 && S.submitDays.includes(arr[i - 1]);
                const today = i === arr.length - 1;
                return (
                  <Fragment key={d}>
                    {i > 0 && <i className={`link ${hit && prevHit ? 'lit' : ''}`} />}
                    <span className={['day', hit && 'hit', today && 'today'].filter(Boolean).join(' ')}
                      title={`${d}${hit ? ' — 제출 ✓' : ''}`}>
                      <b>{Number(d.slice(8))}</b>
                      {hit && <em>🔥</em>}
                    </span>
                  </Fragment>
                );
              })}
            </div>
            <p className="note xs" style={{ marginTop: 12 }}>
              최근 14일 · 불이 이어진 만큼이 연속 기록 · 오늘 칸은 노란 테두리
            </p>
          </div>

          {/* 일일 크레딧 (FR-81/85) */}
          <div className="panel">
            <h3>⚡ 오늘의 크레딧 {S.creditUsed} / {creditLimit}</h3>
            <div className={`credit-bar ${pct >= 90 ? 'warn' : ''}`}>
              <i style={{ width: pct + '%' }} />
            </div>
            <p className="note sm" style={{ marginTop: 10 }}>
              {user.planName} 플랜은 하루 {creditLimit}개 — 매일 자정에 다시 채워져요. 리뷰, 학습카드, 퀴즈, 스킬 추천에 각각 1개씩 쓰입니다.
              {pct < 100 ? '' : ' — 오늘치 소진!'}
            </p>
            {user.planName === 'FREE' && (
              <Link className="btn wh sm" style={{ marginTop: 12, display: 'inline-block' }} to="/app/plan">
                더 필요하면 PRO →
              </Link>
            )}
          </div>

          {/* 약점 요약 (LRN-001) */}
          <div className="panel">
            <h3>🎯 지금 나의 약점</h3>
            {weakness.length === 0 ? (
              <p className="note">
                아직 데이터가 없어요. 리뷰를 받으면 반복 실수가 여기 쌓입니다.
              </p>
            ) : (
              weakness.map((w) => (
                <div key={w.id} className="weak-mini">
                  <div className={`weak-count ${w.occurrenceCount < 4 ? 'mild' : ''}`}>
                    <b>{w.occurrenceCount}회</b>
                  </div>
                  <div className="weak-body">
                    <b style={{ fontSize: 14.5 }}>{w.category}</b>
                    <p className="note sm">{w.language}</p>
                  </div>
                </div>
              ))
            )}
            <div style={{ display: 'flex', gap: 10, marginTop: 16, flexWrap: 'wrap' }}>
              <Link className="btn co sm" to="/app/weakness">약점 통계</Link>
              <Link className="btn wh sm" to="/app/cards">학습카드</Link>
              <Link className="btn wh sm" to="/app/paste">코드 리뷰 받기</Link>
            </div>
          </div>
        </div>
      </div>
    </main>

    {/* 로그인 상태에서도 하단엔 서비스 안내 유지 (요구: 대시보드 + 안내 결합) */}
    <InfoSections />
    </>
  );
}
