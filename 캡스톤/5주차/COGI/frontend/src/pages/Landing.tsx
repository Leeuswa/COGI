/*
 * 랜딩 페이지 (임시)
 * 문구랑 레이아웃만 대충 잡아둠. 캐릭터 이미지, 섹션 디자인은 아직 미정
 * TODO: 코기 캐릭터 영역, 기능 소개 섹션 디자인, 요금제 섹션
 */
import { Link } from 'react-router-dom';

export default function Landing() {
  return (
    <main className="lp-temp">
      <section className="hero">
        <div className="hero-copy">
          <span className="lv">COGI · CODE GUIDE</span>
          <h1>
            어제 받은 코드리뷰를<br />
            오늘의 공부로
          </h1>
          <p className="sub">
            AI가 코드를 리뷰하고, 자주 틀리는 부분을 약점으로 모아서<br />
            학습카드랑 퀴즈로 만들어주는 서비스 (문구 수정 예정)
          </p>
          <div className="cta-row">
            <Link className="btn co" to="/signup">시작하기</Link>
            <Link className="btn wh" to="/login">로그인</Link>
          </div>
        </div>

        <div className="placeholder">
          코기 캐릭터 들어갈 자리<br />(디자인 미정)
        </div>
      </section>

      <section className="todo-sec">
        <h2>주요 기능 (작성중)</h2>
        <ul>
          <li><b>AI 코드 리뷰</b> - GitHub PR이나 코드 붙여넣기로 리뷰 받기</li>
          <li><b>약점 분석</b> - 같은 실수 반복되면 약점으로 기록</li>
          <li><b>학습카드 / 퀴즈</b> - 약점 기반으로 공부</li>
          <li><b>성장 리포트</b> - ???</li>
        </ul>
      </section>
    </main>
  );
}
