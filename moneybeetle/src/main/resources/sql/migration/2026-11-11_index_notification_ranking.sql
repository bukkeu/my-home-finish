USE money_beetle;

-- 이미 DB를 만들어 둔 사람용: 알림 목록·시즌 랭킹 조회용 인덱스 3개를 추가한다 (이슈 #282, #87 후속).
-- - notification.idx_notification_2 (member_id, created_at, notification_id) : 알림 목록을 최신순으로 읽는다
-- - season_participant.idx_season_participant_3 (trading_season_id, asset_rank, season_participant_id) : 시즌 랭킹(순자산 순위)
-- - season_participant.idx_season_participant_4 (trading_season_id, return_rank, season_participant_id) : 시즌 랭킹(수익률 순위)
-- - schema.sql 을 다시 실행하지 않는다 (테이블과 데이터가 모두 삭제된다).
-- - 여러 번 실행해도 결과가 같다. 이미 같은 정의(칼럼, 순서, 오름차순, VISIBLE 까지 같은)의 인덱스가 있으면 건너뛰고, 데이터는 바뀌지 않는다.
-- - 같은 이름이라도 칼럼·순서·정렬 방향(오름차순)·표시 여부(VISIBLE)가 다른 인덱스(예: 예전에 (member_id, created_at) 로 만든 idx_notification_2)는 지우고 새 정의로 다시 만든다.
-- 새로 DB를 만드는 사람은 schema.sql 에 이미 반영되어 있으므로 이 파일을 실행할 필요가 없다.

-- 1) idx_notification_2 (member_id, created_at, notification_id): 정의가 다르면 지우고, 없으면 만든다
SET @sql = IF(
    COALESCE((SELECT GROUP_CONCAT(CONCAT(COLUMN_NAME, ':', COLLATION, ':', IS_VISIBLE) ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND INDEX_NAME = 'idx_notification_2'), '')
        NOT IN ('', 'member_id:A:YES,created_at:A:YES,notification_id:A:YES'),
    'DROP INDEX idx_notification_2 ON notification',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND INDEX_NAME = 'idx_notification_2') = 0,
    'CREATE INDEX idx_notification_2 ON notification (member_id, created_at, notification_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) idx_season_participant_3 (trading_season_id, asset_rank, season_participant_id): 정의가 다르면 지우고, 없으면 만든다
SET @sql = IF(
    COALESCE((SELECT GROUP_CONCAT(CONCAT(COLUMN_NAME, ':', COLLATION, ':', IS_VISIBLE) ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'season_participant' AND INDEX_NAME = 'idx_season_participant_3'), '')
        NOT IN ('', 'trading_season_id:A:YES,asset_rank:A:YES,season_participant_id:A:YES'),
    'DROP INDEX idx_season_participant_3 ON season_participant',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'season_participant' AND INDEX_NAME = 'idx_season_participant_3') = 0,
    'CREATE INDEX idx_season_participant_3 ON season_participant (trading_season_id, asset_rank, season_participant_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) idx_season_participant_4 (trading_season_id, return_rank, season_participant_id): 정의가 다르면 지우고, 없으면 만든다
SET @sql = IF(
    COALESCE((SELECT GROUP_CONCAT(CONCAT(COLUMN_NAME, ':', COLLATION, ':', IS_VISIBLE) ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'season_participant' AND INDEX_NAME = 'idx_season_participant_4'), '')
        NOT IN ('', 'trading_season_id:A:YES,return_rank:A:YES,season_participant_id:A:YES'),
    'DROP INDEX idx_season_participant_4 ON season_participant',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'season_participant' AND INDEX_NAME = 'idx_season_participant_4') = 0,
    'CREATE INDEX idx_season_participant_4 ON season_participant (trading_season_id, return_rank, season_participant_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
