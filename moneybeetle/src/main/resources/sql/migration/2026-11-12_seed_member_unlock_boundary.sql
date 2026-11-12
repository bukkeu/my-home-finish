USE money_beetle;

-- 레벨 해금 경계 확인용 시드 회원 2명 (해금 SP-24 · 레벨 SP-20)
-- 이미 sample-data.sql 을 넣은 DB 에 학습자 2명을 추가한다. 비밀번호는 다른 시드 계정과 같은 test1234 (BCrypt 해시).
--   - 투자직전 (trading-soon@moneybeetle.local) : Lv.7, 경험치 1,050 -> 모의투자(Lv.8) 바로 아래
--   - 청약직전 (housing-soon@moneybeetle.local) : Lv.11, 경험치 2,150 -> 모의 청약(Lv.12) 바로 아래
-- 새로 DB 를 만드는 사람은 sample-data.sql 에 이미 들어 있으므로 이 파일을 실행할 필요가 없다.
-- 이메일로 찾아서, 이미 있는 행은 건너뛴다 (다시 실행해도 결과가 같다).
--   캐시는 0 이라 캐시 원장이 없다. 경험치 원장과 학습 기록은 **이 파일이 회원을 새로 만들었을 때만** 넣는다
--   (이미 있던 회원에 넣으면 member.exp 와 exp_history.exp_after 가 어긋날 수 있다, #350 과 같은 이유). 청약통장은 없으면 만든다.
-- 한 트랜잭션이다 : 중간에 오류가 나면 COMMIT 대신 ROLLBACK 을 실행한다 (Workbench 는 "오류가 나면 멈춤"을 켜고 새 연결에서 실행).

START TRANSACTION;

-- ---------------------------------------------------------------------
-- 투자직전 (Lv.7)
-- ---------------------------------------------------------------------
INSERT INTO member (email, password_hash, nickname, birth_date, role, status, level, exp, cash)
SELECT 'trading-soon@moneybeetle.local', '$2a$10$OKNSjtlUahwwv0zo9TcB9u7sZPPxwoo6duhvwtELedHblmBpqkzoK', '투자직전', '2000-05-20',
       'MEMBER', 'ACTIVE', 7, 1050, 0
WHERE NOT EXISTS (SELECT 1 FROM member WHERE email = 'trading-soon@moneybeetle.local');
SET @created = ROW_COUNT();   -- 바로 위 INSERT 로 회원을 새로 만들었으면 1, 이미 있었으면 0

SET @mid = (SELECT member_id FROM member WHERE email = 'trading-soon@moneybeetle.local');

INSERT INTO subscription_account (member_id)
SELECT @mid WHERE NOT EXISTS (SELECT 1 FROM subscription_account WHERE member_id = @mid);

-- 경험치 원장 : member.exp 1,050 = 누적 Lv.7. 완료한 강의 4 한 건으로 묶는다
INSERT INTO exp_history (member_id, source_type, source_id, amount, exp_after, level_after)
SELECT @mid, 'COURSE_COMPLETE', 4, 1050, 1050, 7
WHERE @created = 1 AND NOT EXISTS (SELECT 1 FROM exp_history WHERE member_id = @mid);

-- 학습 기록 : 경험치 원장과 맞게 강의 4 완료
INSERT INTO learning_progress (member_id, course_id, status, last_lesson_id, last_position_seconds, completed_at)
SELECT @mid, 4, 'COMPLETED', NULL, 0, '2026-11-06 21:00:00'
WHERE @created = 1 AND NOT EXISTS (SELECT 1 FROM learning_progress WHERE member_id = @mid AND course_id = 4);

-- ---------------------------------------------------------------------
-- 청약직전 (Lv.11)
-- ---------------------------------------------------------------------
INSERT INTO member (email, password_hash, nickname, birth_date, role, status, level, exp, cash)
SELECT 'housing-soon@moneybeetle.local', '$2a$10$OKNSjtlUahwwv0zo9TcB9u7sZPPxwoo6duhvwtELedHblmBpqkzoK', '청약직전', '1997-09-03',
       'MEMBER', 'ACTIVE', 11, 2150, 0
WHERE NOT EXISTS (SELECT 1 FROM member WHERE email = 'housing-soon@moneybeetle.local');
SET @created = ROW_COUNT();

SET @mid = (SELECT member_id FROM member WHERE email = 'housing-soon@moneybeetle.local');

INSERT INTO subscription_account (member_id)
SELECT @mid WHERE NOT EXISTS (SELECT 1 FROM subscription_account WHERE member_id = @mid);

-- 경험치 원장 : member.exp 2,150 = 누적 Lv.11. 완료한 강의 4 한 건으로 묶는다
INSERT INTO exp_history (member_id, source_type, source_id, amount, exp_after, level_after)
SELECT @mid, 'COURSE_COMPLETE', 4, 2150, 2150, 11
WHERE @created = 1 AND NOT EXISTS (SELECT 1 FROM exp_history WHERE member_id = @mid);

INSERT INTO learning_progress (member_id, course_id, status, last_lesson_id, last_position_seconds, completed_at)
SELECT @mid, 4, 'COMPLETED', NULL, 0, '2026-11-06 21:00:00'
WHERE @created = 1 AND NOT EXISTS (SELECT 1 FROM learning_progress WHERE member_id = @mid AND course_id = 4);

COMMIT;
