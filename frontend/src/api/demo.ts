/*
 * 테스트 로그인 — 서버가 COGI_DEMO_ENABLED=true 로 켜져 있을 때만 쓰인다.
 * 비밀번호를 보내지 않는다. 서버가 테스트 계정 데이터를 처음 상태로 되돌리고 HttpOnly 쿠키로 토큰을 준다.
 */

// 서버에서 켜져 있는지. 꺼져 있으면 404 라 false
export async function demoLoginEnabled(): Promise<boolean> {
  try {
    const res = await fetch('/api/auth/demo', { credentials: 'include' });
    if (!res.ok) return false;
    const json = await res.json();
    return json?.data?.enabled === true;
  } catch {
    return false;
  }
}

export async function demoLogin(kind: 'user' | 'admin'): Promise<void> {
  const res = await fetch(`/api/auth/demo/${kind}`, { method: 'POST', credentials: 'include' });
  if (!res.ok) {
    let message = '';
    try { message = (await res.json())?.message ?? ''; } catch { /* 본문 없음 */ }
    throw Object.assign(new Error(message || `HTTP ${res.status}`), { status: res.status });
  }
}
