USE money_beetle;

-- 학습 완료·퀴즈 만점·출석 경험치 보상 규칙 등록 (이슈 #68, SP-13·SP-22 확정)
-- - COURSE_COMPLETE  : 강의 학습 완료 30,000캐시 + 150 XP (같은 강의 최초 1회)
-- - QUIZ_PERFECT     : 퀴즈 만점 보너스 30 XP (같은 강의 최초 1회)
-- - ATTENDANCE_DAILY : 출석 경험치 기본값 10 XP. 연속 일수 5일마다 ×2·×3… 하루 상한 ×5(50 XP)는 코드에서 계산
-- 학습 완료·퀴즈 만점 규칙이 없으면 그 보상 지급이 REWARD_RULE_NOT_FOUND 로 실패한다.
-- 이미 있는 규칙은 관리자가 값을 고쳤을 수 있어서 바꾸지 않고, 없을 때만 넣는다. 다시 실행해도 결과가 같다.
-- 새로 DB를 만드는 사람은 sample-data.sql 에 이미 들어 있으므로 이 파일을 실행할 필요가 없다.

INSERT INTO reward_rule (code, name, cash_amount, exp_amount, description, is_active)
SELECT 'COURSE_COMPLETE', '강의 학습 완료', 30000, 150, '같은 강의 최초 1회 (SP-13, SP-22)', 1
WHERE NOT EXISTS (SELECT 1 FROM reward_rule WHERE code = 'COURSE_COMPLETE');

INSERT INTO reward_rule (code, name, cash_amount, exp_amount, description, is_active)
SELECT 'QUIZ_PERFECT', '퀴즈 만점 보너스', 0, 30, '같은 강의 최초 1회 (SP-22)', 1
WHERE NOT EXISTS (SELECT 1 FROM reward_rule WHERE code = 'QUIZ_PERFECT');

INSERT INTO reward_rule (code, name, cash_amount, exp_amount, description, is_active)
SELECT 'ATTENDANCE_DAILY', '출석 경험치', 0, 10, '출석마다. 연속 일수 5일마다 +10, 하루 상한 50 (SP-22)', 1
WHERE NOT EXISTS (SELECT 1 FROM reward_rule WHERE code = 'ATTENDANCE_DAILY');
