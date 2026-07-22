-- 약점 통계(API-042) 검증용 시드 데이터
-- 실제 AI 리뷰를 세 번 돌리지 않고도, reviews/review_issues에 직접 데이터를 심어
-- 약점 페이지가 제대로 집계되는지 눈으로 확인하는 용도. 다 보고 나면 맨 아래 정리(cleanup)로 지우면 원상복구된다.
--
-- 실행: mariadb -u root -p12345678 cogi < backend/seed-weakness-verify.sql
-- (또는 DBeaver/HeidiSQL 같은 툴에 통째로 붙여넣기)

-- ── 0. 내 user_id 찾기 ─────────────────────────────────────────
-- 아래 목록에서 지금 로그인한 계정의 id를 확인해 다음 줄 @uid에 넣는다.
SELECT id, email, nickname FROM users ORDER BY id;

-- ── 1. 대상 사용자 지정 (여기 숫자만 본인 id로 바꾸기) ──────────
SET @uid = 1;

-- ── 2. 시드 심기 ───────────────────────────────────────────────
-- source_code에 표식('__WEAKNESS_SEED__')을 남겨 나중에 이것만 골라 지운다.

-- (A) Java 리뷰 하나
INSERT INTO reviews (target_type, user_id, model_name, language, status, source_code, created_at)
VALUES ('PASTE', @uid, 'claude-haiku-4-5', 'Java', 'COMPLETED', '__WEAKNESS_SEED__', NOW());
SET @rJava = LAST_INSERT_ID();

-- BUG 3건(정상 약점) → 약점 페이지에 "BUG / Java / 3회"로 떠야 함
INSERT INTO review_issues (review_id, category, severity, file_path, line_number, description, status, acknowledged, created_at, updated_at)
VALUES (@rJava, 'BUG', 'MAJOR', 'Foo.java', 10, '시드 버그 1', 'OPEN', 0, NOW(), NOW()),
       (@rJava, 'BUG', 'MAJOR', 'Foo.java', 20, '시드 버그 2', 'OPEN', 0, NOW(), NOW()),
       (@rJava, 'BUG', 'MAJOR', 'Foo.java', 30, '시드 버그 3', 'OPEN', 0, NOW(), NOW());

-- BUG 1건이지만 acknowledged=1(의도한 코드) → 집계에서 제외되어야 함
-- (그래서 위 BUG는 4가 아니라 3으로 세어져야 정상)
INSERT INTO review_issues (review_id, category, severity, file_path, line_number, description, status, acknowledged, created_at, updated_at)
VALUES (@rJava, 'BUG', 'MAJOR', 'Foo.java', 40, '의도한 코드(제외 대상)', 'IGNORED', 1, NOW(), NOW());

-- CONVENTION 2건 → 3회 미만이라 약점 페이지에 안 떠야 함
INSERT INTO review_issues (review_id, category, severity, file_path, line_number, description, status, acknowledged, created_at, updated_at)
VALUES (@rJava, 'CONVENTION', 'MINOR', 'Foo.java', 50, '컨벤션 1', 'OPEN', 0, NOW(), NOW()),
       (@rJava, 'CONVENTION', 'MINOR', 'Foo.java', 60, '컨벤션 2', 'OPEN', 0, NOW(), NOW());

-- (B) Python 리뷰 하나 — 같은 BUG라도 언어가 다르면 따로 잡혀야 함(FR-65)
INSERT INTO reviews (target_type, user_id, model_name, language, status, source_code, created_at)
VALUES ('UPLOAD', @uid, 'claude-haiku-4-5', 'Python', 'COMPLETED', '__WEAKNESS_SEED__', NOW());
SET @rPy = LAST_INSERT_ID();

-- BUG 3건 → "BUG / Python / 3회"로 별도 항목이 떠야 함
INSERT INTO review_issues (review_id, category, severity, file_path, line_number, description, status, acknowledged, created_at, updated_at)
VALUES (@rPy, 'BUG', 'MAJOR', 'bar.py', 5, '파이썬 버그 1', 'OPEN', 0, NOW(), NOW()),
       (@rPy, 'BUG', 'MAJOR', 'bar.py', 15, '파이썬 버그 2', 'OPEN', 0, NOW(), NOW()),
       (@rPy, 'BUG', 'MAJOR', 'bar.py', 25, '파이썬 버그 3', 'OPEN', 0, NOW(), NOW());

-- ── 3. 기대 결과 미리보기 ──────────────────────────────────────
-- 약점 페이지(GET /api/users/me/weakness-stats)가 내려줘야 할 것과 같은 집계.
-- 여기서 BUG/Java=3, BUG/Python=3 두 줄만 나오면(CONVENTION·acknowledged 제외) 정상.
SELECT ri.category, r.language, COUNT(*) AS occurrence_count
FROM review_issues ri
JOIN reviews r ON r.id = ri.review_id
WHERE r.user_id = @uid
  AND ri.acknowledged = 0
GROUP BY ri.category, r.language
HAVING COUNT(*) >= 3;

-- 이제 브라우저에서 '약점' 페이지를 새로고침하면 위와 같은 항목이 보인다.

-- ── 정리(cleanup) — 검증 끝나면 이 블록만 따로 실행해서 지우기 ──
-- DELETE FROM review_issues WHERE review_id IN (SELECT id FROM reviews WHERE source_code = '__WEAKNESS_SEED__');
-- DELETE FROM reviews WHERE source_code = '__WEAKNESS_SEED__';
-- weakness_stats는 약점 페이지를 다시 열면 자동으로 다시 계산되어 비워지므로 따로 지울 필요 없음.
