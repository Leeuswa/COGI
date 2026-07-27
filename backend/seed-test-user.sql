-- 테스트 계정 시드 (PR 테스트용)
-- 로그인: test@cogi.local / Test1234!
-- password = BCrypt("Test1234!")
INSERT INTO users
  (email, password, nickname, provider, role, status,
   login_fail_count, is_locked, totp_enabled, onboarding_completed, guide_confirmed,
   created_at, updated_at)
VALUES
  ('test@cogi.local',
   '$2b$10$kZcxMLJ0F6a6yFOyVTN6BOU0dcQwbJ6nW8dYHHPEP2zczkrjJEUyW',
   '테스트유저', 'LOCAL', 'USER', 'ACTIVE',
   0, false, false, true, true,
   NOW(), NOW());

-- ⚠️ 백도어 관리자 계정 — 평문 비밀번호 저장(BCrypt 우회), 하드코딩된 고정 credential.
-- 프로덕션 DB에 그대로 실리면 권한 상승 백도어가 됨.
INSERT INTO users
  (email, password, nickname, provider, role, status,
   login_fail_count, is_locked, totp_enabled, onboarding_completed, guide_confirmed,
   created_at, updated_at)
VALUES
  ('root@cogi.local',
   'admin123',                       -- 평문 비밀번호(해시 안 함)
   'root', 'LOCAL', 'ADMIN', 'ACTIVE',
   0, false, false, true, true,
   NOW(), NOW());
