USE money_beetle;

-- 이미 DB를 만들어 둔 사람용: 인덱스·쿼리 점검(#87)에서 정한 인덱스 1개를 추가한다.
-- - housing_application.idx_housing_application_2 (applied_at) : 신청 접수번호를 만들 때 오늘 신청 수를 센다
--   (EXPLAIN 으로 확인: 이 인덱스만으로 세므로 테이블 전체를 읽지 않는다)
-- - schema.sql 을 다시 실행하지 않는다 (테이블과 데이터가 모두 삭제된다).
-- - 여러 번 실행해도 결과가 같다 (인덱스가 이미 있으면 건너뛴다). 데이터는 바뀌지 않는다.
-- - 이 파일의 앞선 버전에 있던 idx_notification_2, idx_stock_price_1 은 EXPLAIN 에서 쓰이지 않아 뺐다.
--   이미 만들어 둔 사람은 아래 두 줄로 지워도 된다 (없으면 오류가 나므로 있을 때만 실행).
--     DROP INDEX idx_notification_2 ON notification;
--     DROP INDEX idx_stock_price_1 ON stock_price;
-- 새로 DB를 만드는 사람은 schema.sql 에 이미 반영되어 있으므로 이 파일을 실행할 필요가 없다.

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
