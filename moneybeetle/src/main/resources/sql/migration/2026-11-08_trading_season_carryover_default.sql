USE money_beetle;

-- 이미 DB를 만들어 둔 사람용: trading_season.carryover_rate 의 기본값만 100.00 으로 바꾼다 (SP-63, #76 확정).
-- - 이미 들어 있는 시즌의 carryover_rate 값은 바꾸지 않는다.
-- - schema.sql 을 다시 실행하지 않는다 (테이블과 데이터가 모두 삭제된다).
-- - 여러 번 실행해도 결과가 같다.
-- 새로 DB를 만드는 사람은 schema.sql 에 이미 반영되어 있으므로 이 파일을 실행할 필요가 없다.
ALTER TABLE trading_season ALTER COLUMN carryover_rate SET DEFAULT 100.00;
