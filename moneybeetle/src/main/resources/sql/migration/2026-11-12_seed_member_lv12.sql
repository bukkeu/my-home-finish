USE money_beetle;

-- 모의 청약 시연용 Lv.12 시드 회원 (이슈 #337, 해금 SP-24 · 레벨 SP-20 · 레벨업 캐시 SP-14 · 캐시 원장 SP-15)
-- 이미 sample-data.sql 을 넣은 DB 에 9번째 학습자 "청약준비"(apart@moneybeetle.local / test1234)를 추가한다.
-- 새로 DB 를 만드는 사람은 sample-data.sql 에 이미 들어 있으므로 이 파일을 실행할 필요가 없다.
-- 이메일로 찾아서, 이미 있는 행은 건너뛴다 (다시 실행해도 결과가 같다).
--   이 이메일로 직접 만든 회원이 이미 있으면 회원은 그대로 두고 비어 있는 통장·원장·학습 기록만 채운다.
--   단, 그 회원의 레벨·경험치·캐시가 시드 값과 다르면 원장을 넣지 않고 멈춘다 (아래 안전장치).
-- 한 트랜잭션이다 : 중간에 오류가 나면 COMMIT 대신 ROLLBACK 을 실행한다 (Workbench 는 "오류가 나면 멈춤"을 켜고 새 연결에서 실행).

START TRANSACTION;

INSERT INTO member (email, password_hash, nickname, birth_date, role, status, level, exp, cash)
SELECT 'apart@moneybeetle.local', '$2a$10$OKNSjtlUahwwv0zo9TcB9u7sZPPxwoo6duhvwtELedHblmBpqkzoK', '청약준비', '1998-03-15',
       'MEMBER', 'ACTIVE', 12, 2450, 1200000
WHERE NOT EXISTS (SELECT 1 FROM member WHERE email = 'apart@moneybeetle.local');

SET @mid = (SELECT member_id FROM member WHERE email = 'apart@moneybeetle.local');

-- 안전장치 : 원장을 새로 넣어야 하는데 회원 값이 시드 값(Lv.12 · 경험치 2,450 · 캐시 1,200,000)과 다르면 여기서 멈춘다.
--   원장(cash_history·exp_history)은 시드 값으로 넣으므로, 직접 만든 회원의 값이 다르면 회원 잔액과 원장이 어긋나기 때문이다.
--   멈추면 일부러 "없는 테이블" 오류(apart_seed_member_differs_check_level_exp_cash)를 내서 아래 INSERT 와 COMMIT 이 실행되지 않는다.
--   이때는 ROLLBACK 을 실행하고, 그 회원의 level·exp·cash 를 시드 값으로 고치거나 회원을 지운 뒤 다시 실행한다.
--   원장이 이미 있으면(이 파일을 이미 실행했으면) 검사하지 않는다 : 그 뒤 납입 등으로 캐시가 바뀌어도 다시 실행할 수 있다.
SET @differs = (SELECT (NOT EXISTS (SELECT 1 FROM cash_history WHERE member_id = @mid) AND cash <> 1200000)
                    OR (NOT EXISTS (SELECT 1 FROM exp_history WHERE member_id = @mid) AND (level <> 12 OR exp <> 2450))
                FROM member WHERE member_id = @mid);
SET @sql = IF(@differs, 'SELECT 1 FROM apart_seed_member_differs_check_level_exp_cash', 'DO 0');
PREPARE seed_guard FROM @sql;
EXECUTE seed_guard;
DEALLOCATE PREPARE seed_guard;

-- 청약통장 (잔액 0, 0회차)
INSERT INTO subscription_account (member_id)
SELECT @mid WHERE NOT EXISTS (SELECT 1 FROM subscription_account WHERE member_id = @mid);

-- 캐시 원장 : member.cash 1,200,000 과 맞춘다
INSERT INTO cash_history (member_id, history_type, source_type, source_id, amount, balance_after, reason, admin_id)
SELECT @mid, 'ADMIN_GIVE', 'ADMIN', NULL, 1200000, 1200000, '시드 초기 지급 (로컬 개발용)', 1
WHERE NOT EXISTS (SELECT 1 FROM cash_history WHERE member_id = @mid);

-- 경험치 원장 : member.exp 2,450 = 누적 Lv.12. 완료한 강의 15 한 건으로 묶는다
INSERT INTO exp_history (member_id, source_type, source_id, amount, exp_after, level_after)
SELECT @mid, 'COURSE_COMPLETE', 15, 2450, 2450, 12
WHERE NOT EXISTS (SELECT 1 FROM exp_history WHERE member_id = @mid);

-- 학습 기록 : 경험치 원장과 맞게 강의 15 완료
INSERT INTO learning_progress (member_id, course_id, status, last_lesson_id, last_position_seconds, completed_at)
SELECT @mid, 15, 'COMPLETED', NULL, 0, '2026-11-05 21:00:00'
WHERE NOT EXISTS (SELECT 1 FROM learning_progress WHERE member_id = @mid AND course_id = 15);

COMMIT;
