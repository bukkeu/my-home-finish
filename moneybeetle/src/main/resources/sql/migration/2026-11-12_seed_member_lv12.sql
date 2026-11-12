USE money_beetle;

-- 모의 청약 시연용 Lv.12 시드 회원 (이슈 #337, 해금 SP-24 · 레벨 SP-20 · 레벨업 캐시 SP-14 · 캐시 원장 SP-15)
-- 이미 sample-data.sql 을 넣은 DB 에 9번째 학습자 "청약준비"(apart@moneybeetle.local / test1234)를 추가한다.
-- 새로 DB 를 만드는 사람은 sample-data.sql 에 이미 들어 있으므로 이 파일을 실행할 필요가 없다.
-- 이메일로 찾아서, 이미 있는 행은 건너뛴다 (다시 실행해도 결과가 같다).
--   이 이메일로 직접 만든 회원이 이미 있으면 회원은 그대로 두고 비어 있는 통장·원장·학습 기록만 채운다.
-- 한 트랜잭션이다 : 중간에 오류가 나면 COMMIT 대신 ROLLBACK 을 실행한다 (Workbench 는 "오류가 나면 멈춤"을 켜고 새 연결에서 실행).

START TRANSACTION;

INSERT INTO member (email, password_hash, nickname, birth_date, role, status, level, exp, cash)
SELECT 'apart@moneybeetle.local', '$2a$10$OKNSjtlUahwwv0zo9TcB9u7sZPPxwoo6duhvwtELedHblmBpqkzoK', '청약준비', '1998-03-15',
       'MEMBER', 'ACTIVE', 12, 2450, 1200000
WHERE NOT EXISTS (SELECT 1 FROM member WHERE email = 'apart@moneybeetle.local');

SET @mid = (SELECT member_id FROM member WHERE email = 'apart@moneybeetle.local');

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
