USE money_beetle;

-- 이미 DB를 만들어 둔 사람용: 인덱스·쿼리 점검(#87)에서 정한 인덱스 3개를 추가한다.
-- - notification.idx_notification_2 (member_id, created_at) : 내 알림 목록을 최신순으로 (읽음 여부와 상관없는 정렬)
-- - housing_application.idx_housing_application_2 (applied_at) : 신청 접수번호를 만들 때 오늘 신청 수를 센다
-- - stock_price.idx_stock_price_1 (trading_season_id, turn_no) : 그 턴의 전 종목 가격을 읽는다
-- - schema.sql 을 다시 실행하지 않는다 (테이블과 데이터가 모두 삭제된다).
-- - 여러 번 실행해도 결과가 같다 (인덱스가 이미 있으면 건너뛴다). 데이터는 바뀌지 않는다.
-- 새로 DB를 만드는 사람은 schema.sql 에 이미 반영되어 있으므로 이 파일을 실행할 필요가 없다.

-- 1) notification
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'notification'
        AND INDEX_NAME = 'idx_notification_2') = 0,
    'CREATE INDEX idx_notification_2 ON notification (member_id, created_at)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) housing_application
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'housing_application'
        AND INDEX_NAME = 'idx_housing_application_2') = 0,
    'CREATE INDEX idx_housing_application_2 ON housing_application (applied_at)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) stock_price
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'stock_price'
        AND INDEX_NAME = 'idx_stock_price_1') = 0,
    'CREATE INDEX idx_stock_price_1 ON stock_price (trading_season_id, turn_no)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
