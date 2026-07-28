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
import { Routes, Route, Navigate, Outlet, useLocation } from 'react-router-dom';
import * as api from './api/client';
import { useAuth } from './context/AuthContext';
import { Nav, Footer } from './components/ui';
import ReagreeGate from './pages/auth/ReagreeGate';

import Landing from './pages/Landing';
import GuestReview from './pages/GuestReview';
import Login from './pages/auth/Login';
import Signup from './pages/auth/Signup';
import FindPassword from './pages/auth/FindPassword';
import Otp from './pages/auth/Otp';
import Onboarding from './pages/auth/Onboarding';
import OAuthCallback from './pages/auth/OAuthCallback';
import InviteAccept from './pages/auth/InviteAccept';

import Dashboard from './pages/Dashboard';
import Repos from './pages/review/Repos';
import PrList from './pages/review/PrList';
import PrDetail from './pages/review/PrDetail';
import Studio from './pages/review/Studio';
import ReviewHistory from './pages/review/ReviewHistory';
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
import Faq from './pages/Faq';

function RequireAuth() {
  const { user } = useAuth();
  const loc = useLocation();
  if (!user) return <Navigate to="/login" state={{ from: loc.pathname }} replace />;
  return <Outlet />;
}

function RequireOnboarded() {
  const { user } = useAuth();
  // 필수 약관이 개정된 사용자는 대시보드 진입 전 재동의 게이트를 통과해야 한다 (FR-91). 로그인 상태에서 1회 조회
  const [reagree, setReagree] = useState(null);
  useEffect(() => { api.checkReagreement().then(setReagree); }, []);

  if (!user.onboardingCompleted) return <Navigate to="/onboarding" replace />;
  // 재동의 필요 → 온보딩처럼 전체를 덮는 게이트. 동의 저장 후에만 Outlet(대시보드) 노출.
  if (reagree?.required)
    return <ReagreeGate terms={reagree.terms} onDone={() => setReagree({ required: false, terms: [] })} />;
  return <Outlet />;
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
        <Route path="/repo-invites/accept" element={<InviteAccept />} />

        <Route element={<RequireAuth />}>
          {/* 온보딩은 로그인만 필요 (완료 전 유일하게 갈 수 있는 곳) */}
          <Route path="/onboarding" element={<Onboarding />} />

          <Route element={<RequireOnboarded />}>
            <Route path="/app" element={<Dashboard />} />
            <Route path="/app/repos" element={<Repos />} />
            <Route path="/app/prs" element={<PrList />} />
            <Route path="/app/prs/:prId" element={<PrDetail />} />
            <Route path="/app/paste" element={<Studio />} />
            <Route path="/app/history" element={<ReviewHistory />} />
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
            <Route path="/app/faq" element={<Faq />} />
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
