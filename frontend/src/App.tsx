/*
 * 라우팅 전체 지도.
 *
 *  /            랜딩 (비로그인 홈)
 *  /guest       비로그인 체험 리뷰 (LOC-001)
 *  /login /signup /find-password /otp   인증
 *  /onboarding  최초 설문 (스킵 불가 — 가드가 강제함, FR-15)
 *  /app/...     로그인 후 전 기능
 *
 * 가드 2겹:
 *  RequireAuth      → 비로그인이면 /login
 *  RequireOnboarded → 온보딩 미완료면 /onboarding (메인 진입 차단)
 */
import { useEffect, useState } from 'react';
import { Routes, Route, Navigate, Outlet, useLocation, Link } from 'react-router-dom';
import * as api from './api/client';
import { useAuth } from './context/AuthContext';
import { Nav, Footer } from './components/ui';

import Landing from './pages/Landing';
import GuestReview from './pages/GuestReview';
import Login from './pages/auth/Login';
import Signup from './pages/auth/Signup';
import FindPassword from './pages/auth/FindPassword';
import Otp from './pages/auth/Otp';
import Onboarding from './pages/auth/Onboarding';
import OAuthCallback from './pages/auth/OAuthCallback';

import Dashboard from './pages/Dashboard';
import Repos from './pages/review/Repos';
import PrList from './pages/review/PrList';
import PrDetail from './pages/review/PrDetail';
import Studio from './pages/review/Studio';
import Weakness from './pages/learn/Weakness';
import Cards from './pages/learn/Cards';
import CardDetail from './pages/learn/CardDetail';
import Courses from './pages/learn/Courses';
import Skills from './pages/learn/Skills';
import Growth from './pages/growth/Growth';
import WeeklyReports from './pages/growth/WeeklyReports';
import TeamPage from './pages/team/TeamPage';
import Plan from './pages/plan/Plan';
import BillingSuccess from './pages/plan/BillingSuccess';
import BillingFail from './pages/plan/BillingFail';
import MyPage from './pages/my/MyPage';
import Admin from './pages/admin/Admin';

function RequireAuth() {
  const { user } = useAuth();
  const loc = useLocation();
  if (!user) return <Navigate to="/login" state={{ from: loc.pathname }} replace />;
  return <Outlet />;
}

function RequireOnboarded() {
  const { user } = useAuth();
  // 필수 약관 버전이 올라간 사용자에게 재동의 배너 (FR-91). 로그인 상태에서 1회만 조회
  const [reagree, setReagree] = useState(null);
  useEffect(() => { api.checkReagreement().then(setReagree); }, []);

  if (!user.onboardingCompleted) return <Navigate to="/onboarding" replace />;
  return (
    <>
      {reagree?.required && (
        <div className="reagree-banner" role="alert">
          「{reagree.termTitle}」이 v{reagree.newVersion}으로 개정됐어요. 계속 이용하려면 재동의가 필요합니다.
          <Link to="/app/my" className="btn sm">약관 확인하기</Link>
        </div>
      )}
      <Outlet />
    </>
  );
}

export default function App() {
  return (
    <div className="app-shell">
      <Nav />
      <div className="app-body">
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/guest" element={<GuestReview />} />
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<Signup />} />
        <Route path="/find-password" element={<FindPassword />} />
        <Route path="/otp" element={<Otp />} />
        <Route path="/oauth/callback" element={<OAuthCallback />} />

        <Route element={<RequireAuth />}>
          {/* 온보딩은 로그인만 필요 (완료 전 유일하게 갈 수 있는 곳) */}
          <Route path="/onboarding" element={<Onboarding />} />

          <Route element={<RequireOnboarded />}>
            <Route path="/app" element={<Dashboard />} />
            <Route path="/app/repos" element={<Repos />} />
            <Route path="/app/prs" element={<PrList />} />
            <Route path="/app/prs/:prId" element={<PrDetail />} />
            <Route path="/app/paste" element={<Studio />} />
            <Route path="/app/preview" element={<Navigate to="/app/paste" replace />} /> {/* 스튜디오로 흡수 */}
            <Route path="/app/weakness" element={<Weakness />} />
            <Route path="/app/cards" element={<Cards />} />
            <Route path="/app/cards/:cardId" element={<CardDetail />} />
            <Route path="/app/courses" element={<Courses />} />
            <Route path="/app/skills" element={<Skills />} />
            <Route path="/app/growth" element={<Growth />} />
            <Route path="/app/reports" element={<WeeklyReports />} />
            <Route path="/app/team" element={<TeamPage />} />
            <Route path="/app/plan" element={<Plan />} />
            <Route path="/app/billing/success" element={<BillingSuccess />} /> {/* 토스 결제창 성공 콜백 */}
            <Route path="/app/billing/fail" element={<BillingFail />} /> {/* 토스 결제창 실패 콜백 */}
            <Route path="/app/my" element={<MyPage />} />
            <Route path="/app/admin" element={<Admin />} />
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      </div>
      <Footer />
    </div>
  );
}
