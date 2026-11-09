USE money_beetle;

-- 연속 출석 보상 규칙(ATTENDANCE_STREAK) 등록 (이슈 #12)
-- 연속 5일째부터 매일 10,000캐시 (SP-12, 확정 #76). 이 규칙이 없으면 5일째 출석 때 캐시 지급이 실패한다.
-- 이미 있는 규칙은 관리자가 값을 고쳤을 수 있어서 바꾸지 않고, 없을 때만 넣는다. 다시 실행해도 결과가 같다.

INSERT INTO reward_rule (code, name, cash_amount, exp_amount, description, is_active)
SELECT 'ATTENDANCE_STREAK', '연속 출석 보상', 10000, 0, '연속 5일째부터 매일 지급 (SP-12)', 1
WHERE NOT EXISTS (SELECT 1 FROM reward_rule WHERE code = 'ATTENDANCE_STREAK');
