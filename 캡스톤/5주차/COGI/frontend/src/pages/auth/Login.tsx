/*
 * 로그인 화면
 * 이메일 로그인 + GitHub / 카카오 버튼
 * 화면만 먼저 만듦. 백엔드 로그인 API 붙이면 submit에서 호출하게 바꿀 것
 */
import { useState } from 'react';
import { Link } from 'react-router-dom';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [msg, setMsg] = useState('');

  const submit = (e) => {
    e.preventDefault();
    setMsg('아직 로그인 기능 연결 전이에요.'); // TODO: 로그인 API 연결
  };

  const social = () => setMsg('소셜 로그인은 준비 중이에요.'); // TODO: OAuth 연동

  return (
    <main className="auth-wrap">
      <div className="auth-card">
        <h2>다시 만나서 반가워요</h2>
        <p className="desc">코기가 기다리고 있었어요. 밥 줄 시간입니다.</p>

        <div className="sns">
          <button className="sns-btn github" onClick={social}>GitHub로 계속하기</button>
          <button className="sns-btn kakao" onClick={social}>카카오톡으로 계속하기</button>
        </div>

        <div className="divider">OR EMAIL</div>

        <form className="form" onSubmit={submit}>
          <div>
            <label>아이디 (또는 이메일)</label>
            <input type="text" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </div>
          <div>
            <label>비밀번호</label>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </div>
          {msg && <p className="err">{msg}</p>}
          <button className="btn co" type="submit">로그인</button>
        </form>

        <div className="auth-foot">
          <Link to="/signup">회원가입</Link>
        </div>
      </div>
    </main>
  );
}
