USE money_beetle;

-- 이미 DB를 만들어 둔 사람용: 인덱스·쿼리 점검(#87)에서 정한 인덱스 1개를 추가한다.
-- - housing_application.idx_housing_application_2 (applied_at) : 신청 접수번호를 만들 때 오늘 신청 수를 센다
--   로컬 EXPLAIN(샘플 데이터)에서 이 인덱스를 쓰는 것까지 확인했다 (key = idx_housing_application_2, Using index, type = index).
--   데이터가 많아졌을 때 범위 검색(range)이 되는지는 실제 신청 수 조회의 실행 계획으로 다시 확인할 일이다.
-- - schema.sql 을 다시 실행하지 않는다 (테이블과 데이터가 모두 삭제된다).
-- - 여러 번 실행해도 결과가 같다 (인덱스가 이미 있으면 건너뛴다). 데이터는 바뀌지 않는다.
-- - 이 PR 의 앞선 커밋(cb1806d)에 있던 idx_notification_2, idx_stock_price_1 은 EXPLAIN 에서 쓰이지 않아 뺐다.
--   dev·main 에는 들어간 적이 없고, 앞선 커밋의 마이그레이션을 직접 실행해 로컬 DB 에 만든 사람에게만 있다.
--   그런 사람은 아래 두 줄로 지워도 된다 (인덱스가 있을 때만 실행. 없으면 1091 오류가 난다).
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
