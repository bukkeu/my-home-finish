USE money_beetle;

-- 모의투자 시드 시즌(시즌 7)을 1턴으로 되돌린다 (이슈 #412, 모의투자 시드 #72 담당 이재성과 합의)
-- - 예전 시드는 시즌 7을 5턴 진행 중으로 넣었다. 참가는 1턴까지만 되므로(SP-51) 시드만 넣은 DB 에서는 학습자가 참가할 수 없어
--   모의투자 시연을 할 수 없었다. 현재 턴을 1로 바꾸고 2~5턴 진행 기록을 비운다. 가격·뉴스(12턴 전부)는 그대로 쓴다.
-- - 시드만 넣은 상태(시즌 7이 진행 중이고 참가자가 없음)일 때만 바뀐다. 누가 이미 참가·거래했거나, 정산·종료한 시즌은 건드리지 않는다.
-- - 다시 실행해도 결과가 같다.
-- - 실행한 뒤 서버를 재시작한다 (현재 턴·시세를 서버 메모리에 담아 둔다).
-- 새로 DB 를 만드는 사람은 sample-data.sql 에 이미 1턴으로 들어 있으므로 이 파일을 실행할 필요가 없다.
-- 시즌 날짜(2026-10-19 ~ 11-01)는 지나 있어도 괜찮다 : 참가·주문은 날짜가 아니라 현재 턴과 장 시간(08:00~20:00)으로 판단한다.

UPDATE trading_season
SET current_turn = 1
WHERE trading_season_id = 1
  AND status = 'RUNNING'
  AND current_turn > 1
  AND NOT EXISTS (SELECT 1 FROM season_participant WHERE trading_season_id = 1);

-- 위에서 1턴이 된 경우에만 2턴 이후 진행 기록을 비운다
UPDATE season_turn
SET progressed_at = NULL,
    progressed_by = NULL
WHERE trading_season_id = 1
  AND turn_no > 1
  AND (SELECT current_turn FROM trading_season WHERE trading_season_id = 1) = 1
  AND NOT EXISTS (SELECT 1 FROM season_participant WHERE trading_season_id = 1);
