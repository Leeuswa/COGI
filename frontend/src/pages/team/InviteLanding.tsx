/*
 * 초대 링크 랜딩 (/invite/:code)
 * 팀장이 공유한 링크의 착지점. 흐름:
 *   비로그인 → [로그인하고 참여] / [회원가입하고 참여] → 돌아와서 신청
 *   로그인   → [합류 신청] → PENDING → 팀장이 팀 페이지에서 수락해야 정식 멤버
 */
import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import * as api from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { useGame } from '../../context/GameContext';

export default function InviteLanding() {
  const { code } = useParams();
  const { user } = useAuth();
  const { notify } = useGame();
  const nav = useNavigate();
  const [info, setInfo] = useState(null);   // { teamName, memberCount, valid }
  const [done, setDone] = useState(false);  // 신청 완료
  const [busy, setBusy] = useState(false);

  useEffect(() => { api.getInviteInfo(code).then(setInfo); }, [code]);

  const join = async () => {
    setBusy(true);
    try {
      await api.requestJoinTeam(code);
      setDone(true);
      notify('합류 신청 완료! 팀장이 수락하면 알려드릴게요');
    } finally { setBusy(false); }
  };

  return (
    <main className="auth-wrap">
      <div className="auth-card">
        {!info ? (
          <p className="note">초대장을 확인하는 중…</p>
        ) : !info.valid ? (
          <>
            <h2>유효하지 않은 초대예요</h2>
            <p className="desc">링크가 만료됐거나 잘못 입력됐어요.{'\n'}팀장에게 새 링크를 요청해주세요.</p>
            <Link className="btn wh" to="/">홈으로</Link>
          </>
        ) : done ? (
          <>
            <h2>🎉 신청 완료!</h2>
            <p className="desc">
              <b>{info.teamName}</b> 팀장에게 신청이 전달됐어요.{'\n'}수락되면 팀 페이지에서 확인할 수 있습니다.
            </p>
            <button className="btn co" onClick={() => nav('/app/team')}>팀 페이지로</button>
          </>
        ) : (
          <>
            <h2>🐕 팀 초대장</h2>
            <p className="desc">
              <b>{info.teamName}</b> ({info.memberCount}명)에서 함께하자고 해요.{'\n'}
              합류하면 팀 PR 리뷰와 성장 통계를 같이 봅니다.
            </p>
            {user ? (
              <button className="btn co" onClick={join} disabled={busy}>
                {busy ? '신청 중…' : `${info.teamName}에 합류 신청`}
              </button>
            ) : (
              <>
                {/* 비로그인: 로그인/가입 후 이 페이지로 복귀 (RequireAuth 의 from 과 같은 원리) */}
                {/* 비회원은 가입이 기본 동선 — 가입·온보딩을 마치면 이 초대 화면으로 자동 복귀 */}
                <div className="sns">
                  <button className="btn co" onClick={() => nav('/signup', { state: { from: `/invite/${code}` } })}>
                    회원가입하고 참여하기
                  </button>
                </div>
                <p className="note sm" style={{ marginTop: 14 }}>
                  가입과 온보딩을 마치면 이 화면으로 돌아와 바로 합류할 수 있어요.{'\n'}
                  이미 계정이 있다면{' '}
                  <button className="term-link" onClick={() => nav('/login', { state: { from: `/invite/${code}` } })}>
                    로그인하고 참여 →
                  </button>
                </p>
              </>
            )}
          </>
        )}
      </div>
    </main>
  );
}
