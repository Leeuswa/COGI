-- ===== 약점 통계(API-042) + 학습카드(LRN-002) 검증용 시드 =====
--
-- ★ 실행 전 필수: 백엔드를 학습카드 코드로 한 번 재시작하세요.
--   ddl-auto=update가 learning_cards / learning_card_quizzes / quiz_submissions / user_streaks 테이블을
--   자동 생성합니다. 재시작 전이면 아래 (C) 카드 INSERT가 "테이블 없음"으로 실패합니다.
--
-- 실행: mariadb -u root -p12345678 cogi < backend/seed-weakness-verify.sql
--
-- 확인 흐름:
--   1) (A)(B) 약점 시드 → '약점' 페이지 새로고침 → BUG/Java, BUG/Python 표시 + "학습카드 만들기" 버튼
--   2) 그 버튼 클릭 → 실제 AI 호출로 카드 생성(백엔드 AI 키 필요) → 상세로 이동. 이게 '약점→생성' 검증.
--   3) (C) 미리 심은 카드 → '보관함(Cards)'과 카드 상세를 AI 없이 바로 확인.

-- (0) plans 모델 정렬 — DB 공용, 1회만 (재실행해도 안전)
--    plans.allowed_models가 실제 AiModel enum id와 달라서(gemini/grok/"claude-opus 4.5" 등)
--    리뷰·카드·퀴즈 어떤 AI 호출도 모델을 못 찾던 문제를 바로잡는다. enum id로 통일.
UPDATE plans SET allowed_models='claude-haiku-4-5,gpt-5.6-luna' WHERE name='FREE';
UPDATE plans SET allowed_models='claude-haiku-4-5,gpt-5.6-luna,claude-sonnet-5,gpt-5.6-terra' WHERE name='PRO';
UPDATE plans SET allowed_models='claude-haiku-4-5,gpt-5.6-luna,claude-sonnet-5,gpt-5.6-terra,claude-opus-4-8,gpt-5.6-sol' WHERE name='MAX';

-- ↓↓↓ 여기 이메일만 본인 계정으로 바꾸세요 ↓↓↓
SET @email = 'j.sangyeon6@gmail.com';

SET @uid = (SELECT id FROM users WHERE email = @email);

-- 이전 시드 정리(재실행해도 중복 안 쌓이게)
DELETE FROM review_issues WHERE review_id IN
  (SELECT id FROM reviews WHERE source_code='__WEAKNESS_SEED__' AND user_id=@uid);
DELETE FROM reviews WHERE source_code='__WEAKNESS_SEED__' AND user_id=@uid;
DELETE FROM learning_cards WHERE user_id=@uid AND category='널 안전성 (테스트 카드)';

-- (A) Java 리뷰 1건 — BUG 3건(약점) + acknowledged 1건(제외) + CONVENTION 2건(3회 미만 제외)
INSERT INTO reviews (target_type, user_id, model_name, language, status, source_code, created_at)
VALUES ('PASTE', @uid, 'claude-haiku-4-5', 'Java', 'COMPLETED', '__WEAKNESS_SEED__', NOW());
SET @rJava = LAST_INSERT_ID();
INSERT INTO review_issues (review_id, category, severity, file_path, line_number, description, status, acknowledged, created_at, updated_at)
VALUES (@rJava,'BUG','MAJOR','Foo.java',10,'시드 버그 1','OPEN',0,NOW(),NOW()),
       (@rJava,'BUG','MAJOR','Foo.java',20,'시드 버그 2','OPEN',0,NOW(),NOW()),
       (@rJava,'BUG','MAJOR','Foo.java',30,'시드 버그 3','OPEN',0,NOW(),NOW()),
       (@rJava,'BUG','MAJOR','Foo.java',40,'의도한 코드(제외)','IGNORED',1,NOW(),NOW()),
       (@rJava,'CONVENTION','MINOR','Foo.java',50,'컨벤션 1','OPEN',0,NOW(),NOW()),
       (@rJava,'CONVENTION','MINOR','Foo.java',60,'컨벤션 2','OPEN',0,NOW(),NOW());

-- (B) Python 리뷰 1건 — 언어가 다르면 따로 잡힘
INSERT INTO reviews (target_type, user_id, model_name, language, status, source_code, created_at)
VALUES ('UPLOAD', @uid, 'claude-haiku-4-5', 'Python', 'COMPLETED', '__WEAKNESS_SEED__', NOW());
SET @rPy = LAST_INSERT_ID();
INSERT INTO review_issues (review_id, category, severity, file_path, line_number, description, status, acknowledged, created_at, updated_at)
VALUES (@rPy,'BUG','MAJOR','bar.py',5,'파이썬 버그 1','OPEN',0,NOW(),NOW()),
       (@rPy,'BUG','MAJOR','bar.py',15,'파이썬 버그 2','OPEN',0,NOW(),NOW()),
       (@rPy,'BUG','MAJOR','bar.py',25,'파이썬 버그 3','OPEN',0,NOW(),NOW());

-- 약점 집계 기대 결과: BUG/Java=3, BUG/Python=3 두 줄이면 정상
SELECT ri.category, r.language, COUNT(*) AS occurrence_count
FROM review_issues ri JOIN reviews r ON r.id = ri.review_id
WHERE r.user_id = @uid AND ri.acknowledged = 0
GROUP BY ri.category, r.language
HAVING COUNT(*) >= 3;

-- (C) 학습카드 1장 미리 심기 — 보관함/상세 화면을 AI 없이 확인용
--     (약점→생성 라이브 테스트와 별개. model_name을 넣어야 이 카드로 퀴즈 생성도 가능)
INSERT INTO learning_cards
  (user_id, category, level, language, model_name,
   concept_content, example_content, bad_example, good_example, key_points,
   grade, correct_count, is_bookmarked, is_completed, created_at)
VALUES
  (@uid, '널 안전성 (테스트 카드)', 'BEGINNER', 'TypeScript', 'claude-haiku-4-5',
   'null/undefined를 안전하게 다루는 법. ?. 로 접근을 보호하고 ?? 로 기본값을 채운다.',
   'function greet(user) {\n  return `hi, ${user?.name ?? ''손님''}`;\n}',
   'function greet(user) {\n  return `hi, ${user.name}`;\n}',
   'function greet(user) {\n  return `hi, ${user?.name ?? ''손님''}`;\n}',
   '?. 는 접근을 안전하게, ?? 는 기본값을 채운다\n|| 는 0·빈문자열·false까지 기본값으로 바꿔버린다\n근본 해결은 왜 null이 들어왔는지 찾는 것',
   'RED', 0, 0, 0, NOW());

-- 심은 카드 확인
SELECT id, category, language, grade, correct_count, model_name FROM learning_cards WHERE user_id=@uid;

-- ── 정리(cleanup) — 검증 끝나면 이 블록만 따로 실행 ──
-- SET @email = 'j.sangyeon6@gmail.com';
-- SET @uid = (SELECT id FROM users WHERE email = @email);
-- DELETE FROM review_issues WHERE review_id IN (SELECT id FROM reviews WHERE source_code='__WEAKNESS_SEED__' AND user_id=@uid);
-- DELETE FROM reviews WHERE source_code='__WEAKNESS_SEED__' AND user_id=@uid;
-- DELETE FROM learning_cards WHERE user_id=@uid AND category='널 안전성 (테스트 카드)';
-- weakness_stats는 약점 페이지를 다시 열면 자동 재계산되어 정리됨
