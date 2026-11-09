USE money_beetle;

-- 이미 DB를 만들어 둔 사람용: 청약 인정금액 컬럼을 추가한다 (SP-70, #76 확정).
-- - subscription_account.recognized_amount : 누적 인정금액
-- - subscription_deposit.recognized_amount : 이번 납입의 인정금액
-- - subscription_deposit.amount 컬럼 주석을 '1회 20,000~500,000 (SP-70)' 으로 바꾼다.
-- - 이미 들어 있는 납입 내역은 인정금액을 min(amount, 250000) 으로 채우고, 통장의 누적 인정금액은 그 합으로 맞춘다.
-- - schema.sql 을 다시 실행하지 않는다 (테이블과 데이터가 모두 삭제된다).
-- - 여러 번 실행해도 결과가 같다 (컬럼이 이미 있으면 추가를 건너뛴다).
-- 새로 DB를 만드는 사람은 schema.sql 에 이미 반영되어 있으므로 이 파일을 실행할 필요가 없다.

-- 1) subscription_account.recognized_amount
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'subscription_account'
        AND COLUMN_NAME = 'recognized_amount') = 0,
    "ALTER TABLE subscription_account ADD COLUMN recognized_amount BIGINT NOT NULL DEFAULT 0 COMMENT '누적 인정금액 (SP-70)' AFTER balance",
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) subscription_deposit.recognized_amount (기본값 없음: 납입할 때 항상 값을 넣는다)
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'subscription_deposit'
        AND COLUMN_NAME = 'recognized_amount') = 0,
    "ALTER TABLE subscription_deposit ADD COLUMN recognized_amount INT NOT NULL COMMENT '이번 납입의 인정금액 (SP-70)' AFTER amount",
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) amount 컬럼 주석 변경 (타입·NULL 여부는 그대로)
ALTER TABLE subscription_deposit
    MODIFY COLUMN amount INT NOT NULL COMMENT '납입 캐시 (1회 20,000~500,000, SP-70)';

-- 4) 기존 납입 내역 보정 (새 컬럼이 0 으로 채워진 행만)
UPDATE subscription_deposit
   SET recognized_amount = LEAST(amount, 250000)
 WHERE recognized_amount = 0;

UPDATE subscription_account a
   SET a.recognized_amount = COALESCE(
       (SELECT SUM(d.recognized_amount)
          FROM subscription_deposit d
         WHERE d.subscription_account_id = a.subscription_account_id), 0);
