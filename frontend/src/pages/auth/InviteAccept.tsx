/*
 * 이메일 레포 초대 링크 랜딩 (/repo-invites/accept?token=...)
 * - 비로그인 상태에서 이메일 속 링크를 클릭해 도착하는 페이지.
 * - 토큰으로 초대 정보를 조회해, 그 이메일로 기존 COGI 계정이 있는지에 따라
 *   GitHub 신규가입(케이스②) / 로그인 후 GitHub 연동(케이스③)으로 안내를 분기한다.
 * - 실제 수락(계정 매칭)은 이 페이지가 아니라 GitHub 가입/연동 완료 시점의
 *   autoMatchPendingInvitations 훅이 처리한다 — 여기선 안내만 한다.
 */
import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import * as api from '../../api/client';

export default function InviteAccept() {
  const [params] = useSearchParams();
  const token = params.get('token') || '';

  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');
  const [info, setInfo] = useState(null);

  useEffect(() => {
    if (!token) {
      setErr('초대 링크가 올바르지 않아요.');
      setLoading(false);
      return;
    }
    api.lookupInvitationByToken(token)
      .then(setInfo)
      .catch((ex) => setErr(ex.message || '초대 링크를 확인할 수 없어요.'))
      .finally(() => setLoading(false));
  }, [token]);

  const goGithubSignup = () => {
    window.location.href = api.githubOAuthUrl();
  };

  return (
    <main className="auth-wrap">
      <div className="auth-card">
        <h2>레포 팀원 초대</h2>

        {loading && <p className="desc">초대 정보를 확인하는 중이에요…</p>}

        {!loading && err && (
          <>
            <p className="err">{err}</p>
            <div className="auth-foot">
              <Link to="/">홈으로</Link>
            </div>
          </>
        )}

        {!loading && !err && info && (
          info.emailHasAccount ? (
            <>
              <p className="desc">
                <b>{info.repoName}</b> 레포에 초대됐어요. 이미 가입된 이메일이라, 로그인 후 마이페이지에서 GitHub 연동을 완료해주세요.
              </p>
              <Link className="btn co" to="/login">로그인하러 가기</Link>
            </>
          ) : (
            <>
              <p className="desc">
                <b>{info.repoName}</b> 레포에 초대됐어요. GitHub 계정으로 가입하면 자동으로 팀에 합류돼요.
              </p>
              <div className="sns">
                <button className="sns-btn github" onClick={goGithubSignup}>
                  GitHub로 가입하기
                </button>
              </div>
            </>
          )
        )}
      </div>
    </main>
  );
}
