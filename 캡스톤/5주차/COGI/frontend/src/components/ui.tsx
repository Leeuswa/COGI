/*
 * 공용 컴포넌트 (네비, 푸터)
 */
import { Link } from 'react-router-dom';

export function Nav() {
  return (
    <nav className="gnb">
      <Link className="logo" to="/">
        <img src="/logo.png" alt="COGI Code Guide" />
      </Link>
      <div className="links">
        <Link to="/login">로그인</Link>
        <Link className="btn co sm" to="/signup">회원가입</Link>
      </div>
    </nav>
  );
}

export function Footer() {
  return (
    <footer className="ft">
      <div>Team Fable · 정상연 · 이수환 · 홍성찬 · 유지환</div>
      <div>COGI · Code Guide</div>
    </footer>
  );
}
