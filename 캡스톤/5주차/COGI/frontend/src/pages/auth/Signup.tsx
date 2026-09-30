/*
 * 회원가입 화면
 * 이메일 / 비밀번호 / 비밀번호 확인 + 약관 동의
 * 이메일 인증코드 단계는 다음에 추가 (인증 방식 조사 중)
 */
import { useState } from 'react';
import { Link } from 'react-router-dom';

export default function Signup() {
  const [email, setEmail] = useState('');
  const [nickname, setNickname] = useState('');
  const [pw, setPw] = useState('');
  const [pw2, setPw2] = useState('');
  const [agree, setAgree] = useState(false);
  const [msg, setMsg] = useState('');

  const submit = (e) => {
    e.preventDefault();
    if (pw !== pw2) return setMsg('비밀번호가 서로 달라요.');
    if (!agree) return setMsg('약관에 동의해주세요.');
    setMsg('아직 회원가입 기능 연결 전이에요.'); // TODO: 회원가입 API 연결
  };

  return (
    <main className="auth-wrap">
      <div className="auth-card">
        <h2>코기 입양 신청서</h2>
        <p className="desc">가입하면 알 하나를 받아요. 첫 리뷰를 받으면 깨어납니다.</p>

        <form className="form" onSubmit={submit}>
          <div>
            <label>이메일</label>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </div>
          <div>
            <label>닉네임 (선택)</label>
            <input type="text" value={nickname} onChange={(e) => setNickname(e.target.value)} />
          </div>
          <div>
            <label>비밀번호</label>
            <input type="password" value={pw} onChange={(e) => setPw(e.target.value)} required />
          </div>
          <div>
            <label>비밀번호 확인</label>
            <input type="password" value={pw2} onChange={(e) => setPw2(e.target.value)} required />
          </div>
          <label className="check-row">
            <input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} />
            이용약관 및 개인정보 처리방침 동의 (필수)
          </label>
          {msg && <p className="err">{msg}</p>}
          <button className="btn co" type="submit">가입하기</button>
        </form>

        <div className="auth-foot">
          이미 계정이 있나요? <Link to="/login">로그인</Link>
        </div>
      </div>
    </main>
  );
}
