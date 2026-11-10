USE money_beetle;

-- 시즌 랭킹 보상 규칙 등록 (이슈 #10, SP-64 확정 #76)
-- - RANKING_RANK_1 : 시즌 랭킹 1위 50,000캐시
-- - RANKING_RANK_2 : 시즌 랭킹 2위 30,000캐시
-- - RANKING_RANK_3 : 시즌 랭킹 3위 10,000캐시
--   순자산·수익률 랭킹 각각의 순위에 같은 규칙을 쓰고, 두 랭킹에 모두 들면 두 번 받는다. 랭킹 공개 때 RewardService.grantRankingReward 로 지급한다.
-- 규칙이 없으면 랭킹 보상 지급이 REWARD_RULE_NOT_FOUND 로 실패한다.
-- 이미 있는 규칙은 관리자가 값을 고쳤을 수 있어서 바꾸지 않고, 없을 때만 넣는다. 다시 실행해도 결과가 같다.
-- 캐시 내역 원인 종류(cash_history.source_type)에 RANKING 이 늘어서 컬럼 설명도 맞춘다 (설명만 바뀌고 데이터는 그대로다).
-- 새로 DB를 만드는 사람은 schema.sql·sample-data.sql 에 이미 들어 있으므로 이 파일을 실행할 필요가 없다.

INSERT INTO reward_rule (code, name, cash_amount, exp_amount, description, is_active)
SELECT 'RANKING_RANK_1', '시즌 랭킹 1위 보상', 50000, 0, '순자산·수익률 랭킹 각각의 1위. 랭킹 공개 때 지급 (SP-64)', 1
WHERE NOT EXISTS (SELECT 1 FROM reward_rule WHERE code = 'RANKING_RANK_1');

INSERT INTO reward_rule (code, name, cash_amount, exp_amount, description, is_active)
SELECT 'RANKING_RANK_2', '시즌 랭킹 2위 보상', 30000, 0, '순자산·수익률 랭킹 각각의 2위. 랭킹 공개 때 지급 (SP-64)', 1
WHERE NOT EXISTS (SELECT 1 FROM reward_rule WHERE code = 'RANKING_RANK_2');

INSERT INTO reward_rule (code, name, cash_amount, exp_amount, description, is_active)
SELECT 'RANKING_RANK_3', '시즌 랭킹 3위 보상', 10000, 0, '순자산·수익률 랭킹 각각의 3위. 랭킹 공개 때 지급 (SP-64)', 1
WHERE NOT EXISTS (SELECT 1 FROM reward_rule WHERE code = 'RANKING_RANK_3');

ALTER TABLE cash_history
    MODIFY COLUMN source_type varchar(30) NOT NULL
    COMMENT 'ATTENDANCE / COURSE_COMPLETE / LEVEL_UP / SEASON_JOIN / SUBSCRIPTION_DEPOSIT / RANKING / ADMIN';
