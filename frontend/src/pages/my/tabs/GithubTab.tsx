/*
 * [마이페이지 탭] GitHub 연동 (AUTH-007, API-013, FR-13~14)
 * 이메일 가입자의 추가 연동. 연동되면 레포/PR 자동 리뷰 메뉴가 열린다.
 */
export default function GithubTab({ user, onLink, busy, error }) {
  return (
    <div className="panel">
      <h3>GitHub 계정 연동</h3>
      {user.githubUsername ? (
        <p style={{ fontSize: 13.5, lineHeight: 2 }}>
          <span className="chip low">LINKED</span> @{user.githubUsername} 로 연동되어 있어요.<br />
          레포 연동과 PR 자동 리뷰를 사용할 수 있습니다.
        </p>
      ) : (
        <>
          <p style={{ fontSize: 13.5, lineHeight: 2, marginBottom: 16 }}>
            이메일로 가입하셨네요. GitHub를 연동하면 레포 목록을 불러오고 PR마다 자동 리뷰가 돌아갑니다.
            단, 이미 다른 계정에 연결된 GitHub은 연동할 수 없어요.
          </p>
          {error && <p style={{ marginBottom: 12, color: '#d92d20', fontWeight: 500 }}>{error}</p>}
          <button className="btn co" onClick={onLink} disabled={busy}>
            {busy ? '연동 중…' : 'GitHub 연동하기'}
          </button>
        </>
      )}
    </div>
  );
}
