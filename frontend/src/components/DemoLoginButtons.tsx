/*
 * 로그인 화면의 「유저 테스트 로그인」 「관리자 테스트 로그인」 버튼.
 * 서버에서 테스트 로그인이 꺼져 있으면 아무것도 그리지 않는다.
 * 모양은 바로 위 소셜 버튼과 같은 sns-btn (흰 바탕 email 변형 — 이미 app.css 에 있던 것)을 그대로 쓴다.
 */
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as api from '../api/client';
import { demoLogin, demoLoginEnabled } from '../api/demo';
import { useAuth } from '../context/AuthContext';

export default function DemoLoginButtons({ onError }: { onError: (msg: string) => void }) {
  const { signIn } = useAuth();
  const nav = useNavigate();
  const [enabled, setEnabled] = useState(false);
  const [busy, setBusy] = useState('');

  useEffect(() => {
    let alive = true;
    demoLoginEnabled().then((on) => { if (alive) setEnabled(on); });
    return () => { alive = false; };
  }, []);

  if (!enabled) return null;

  const go = async (kind: 'user' | 'admin') => {
    if (busy) return;
    onError('');
    setBusy(kind);
    try {
      await demoLogin(kind);
      // 이메일 로그인과 같은 순서 — 쿠키가 생긴 뒤 프로필을 받아 세션을 연다
      const user = await api.getProfile();
      signIn(user);
      nav(kind === 'admin' ? '/app/admin' : '/app');
    } catch (ex) {
      onError(ex?.message || '테스트 로그인에 실패했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setBusy('');
    }
  };

  return (
    <>
      <button className="sns-btn email" type="button" disabled={!!busy} onClick={() => go('user')}>
        {busy === 'user' ? '준비 중…' : '유저 테스트 로그인'}
      </button>
      <button className="sns-btn email" type="button" disabled={!!busy} onClick={() => go('admin')}>
        {busy === 'admin' ? '준비 중…' : '관리자 테스트 로그인'}
      </button>
    </>
  );
}
