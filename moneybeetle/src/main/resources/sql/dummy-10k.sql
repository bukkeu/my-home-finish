-- comment.
--   담당 : SCM 서수영 (#285, 리팩토링 전 기준 수치 측정)
--   금칙어·카테고리·강의를 각각 1만 건 넣는다 (코드 점검표 R-3 "수만 건 기준 확인")
--   - 로컬 측정용이다. 발표·시연 DB 에는 넣지 않는다. schema.sql·sample-data.sql 을 먼저 넣은 DB 에서 실행한다
--   - 넣은 행은 이 스크립트가 만드는 이름 그대로로 구분한다 : 강의 "[더미] 금리 강의 ", 카테고리 "[더미] 카테고리 "·"[더미] 소주제 ", 금칙어 "더미금칙어"
--     (손으로 만든 "[더미] …" 강의는 건드리지 않는다. 같은 이름 형식으로 직접 만든 행까지 지키려면 이 형식을 쓰지 않는다)
--   - 다시 실행하면 지난번 더미를 먼저 지우고 새로 넣는다 (여러 번 실행해도 1만 건)
--   - 더미 강의를 스크랩·문의·학습·검수한 기록이 있으면 그 기록(더미 강의에 걸린 것만)을 먼저 지운다. 다른 강의의 기록은 그대로 둔다
--     더미 강의에 영상·퀴즈를 직접 만들었다면 그것은 지우지 않으니 먼저 지운다
--   - 맨 아래 "지우기" 구역만 실행하면 더미를 모두 지운다
--   실행 : mysql -u <계정> -p money_beetle < moneybeetle/src/main/resources/sql/dummy-10k.sql
--
--   넣는 내용
--   - forbidden_word 10,000 : 더미금칙어00001 ~ 더미금칙어10000
--   - category 10,000       : 상위 카테고리 100 ([더미] 카테고리 001~100) + 소주제 9,900 (상위마다 99개)
--   - course 10,000         : 허가(APPROVED)·공개 강의. 소주제 9,900 개에 차례로 나눠 넣는다
--                             제목 "[더미] 금리 강의 00001" → 강의 검색(API 28) "금리" 로 1만 건이 걸린다
--                             강사는 시드 강사(seoyeon@moneybeetle.local)

SET SESSION cte_max_recursion_depth = 20000;

-- ---------- 지난번 더미 지우기 (더미 강의에 걸린 기록 → 강의 → 소주제 → 상위 카테고리 → 금칙어) ----------
DELETE FROM scrap WHERE course_id IN (SELECT course_id FROM course WHERE title LIKE '[더미] 금리 강의 %');
DELETE FROM inquiry_reply WHERE inquiry_id IN (SELECT inquiry_id FROM inquiry WHERE course_id IN (SELECT course_id FROM course WHERE title LIKE '[더미] 금리 강의 %'));
DELETE FROM inquiry WHERE course_id IN (SELECT course_id FROM course WHERE title LIKE '[더미] 금리 강의 %');
DELETE FROM learning_progress WHERE course_id IN (SELECT course_id FROM course WHERE title LIKE '[더미] 금리 강의 %');
DELETE FROM course_review WHERE course_id IN (SELECT course_id FROM course WHERE title LIKE '[더미] 금리 강의 %');
DELETE FROM course WHERE title LIKE '[더미] 금리 강의 %';
DELETE FROM category WHERE name LIKE '[더미] 소주제 %' AND parent_id IS NOT NULL;
DELETE FROM category WHERE name LIKE '[더미] 카테고리 %' AND parent_id IS NULL;
DELETE FROM forbidden_word WHERE word LIKE '더미금칙어%';

-- ---------- 금칙어 10,000 ----------
INSERT INTO forbidden_word (word)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 10000)
SELECT CONCAT('더미금칙어', LPAD(n, 5, '0')) FROM seq;

-- ---------- 카테고리 : 상위 100 ----------
INSERT INTO category (parent_id, name, depth, sort_order, is_active)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 100)
SELECT NULL, CONCAT('[더미] 카테고리 ', LPAD(n, 3, '0')), 1, 1000 + n, 1 FROM seq;

-- ---------- 카테고리 : 소주제 9,900 (상위마다 99개) ----------
INSERT INTO category (parent_id, name, depth, sort_order, is_active)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 99)
SELECT p.category_id, CONCAT('[더미] 소주제 ', RIGHT(p.name, 3), '-', LPAD(seq.n, 2, '0')), 2, seq.n, 1
FROM category p
CROSS JOIN seq
WHERE p.name LIKE '[더미] 카테고리 %' AND p.parent_id IS NULL;

-- ---------- 강의 10,000 (허가·공개) ----------
SET @instructor_id = (SELECT member_id FROM member WHERE email = 'seoyeon@moneybeetle.local');

INSERT INTO course (instructor_id, category_id, title, description, status, is_public, submitted_at, approved_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 10000),
     sub AS (SELECT category_id, ROW_NUMBER() OVER (ORDER BY category_id) AS rn
             FROM category WHERE name LIKE '[더미] 소주제 %')
SELECT @instructor_id, sub.category_id, CONCAT('[더미] 금리 강의 ', LPAD(seq.n, 5, '0')), '측정용 더미 강의 (#285)',
       'APPROVED', 1, NOW(), NOW()
FROM seq
JOIN sub ON sub.rn = ((seq.n - 1) % 9900) + 1;

-- ---------- 넣은 건수 확인 ----------
SELECT 'forbidden_word' AS tbl, COUNT(*) AS dummy_rows FROM forbidden_word WHERE word LIKE '더미금칙어%'
UNION ALL SELECT 'category', COUNT(*) FROM category WHERE name LIKE '[더미] 카테고리 %' OR name LIKE '[더미] 소주제 %'
UNION ALL SELECT 'course', COUNT(*) FROM course WHERE title LIKE '[더미] 금리 강의 %';

-- ---------- 지우기 (측정이 끝나면 아래 줄만 실행. 맨 위 "지난번 더미 지우기" 와 같다) ----------
-- DELETE FROM scrap WHERE course_id IN (SELECT course_id FROM course WHERE title LIKE '[더미] 금리 강의 %');
-- DELETE FROM inquiry_reply WHERE inquiry_id IN (SELECT inquiry_id FROM inquiry WHERE course_id IN (SELECT course_id FROM course WHERE title LIKE '[더미] 금리 강의 %'));
-- DELETE FROM inquiry WHERE course_id IN (SELECT course_id FROM course WHERE title LIKE '[더미] 금리 강의 %');
-- DELETE FROM learning_progress WHERE course_id IN (SELECT course_id FROM course WHERE title LIKE '[더미] 금리 강의 %');
-- DELETE FROM course_review WHERE course_id IN (SELECT course_id FROM course WHERE title LIKE '[더미] 금리 강의 %');
-- DELETE FROM course WHERE title LIKE '[더미] 금리 강의 %';
-- DELETE FROM category WHERE name LIKE '[더미] 소주제 %' AND parent_id IS NOT NULL;
-- DELETE FROM category WHERE name LIKE '[더미] 카테고리 %' AND parent_id IS NULL;
-- DELETE FROM forbidden_word WHERE word LIKE '더미금칙어%';
