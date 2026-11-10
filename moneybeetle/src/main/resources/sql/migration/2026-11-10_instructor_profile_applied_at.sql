USE money_beetle;

-- 강사 정보에 강사 가입 신청 시각(applied_at)을 추가한다 (이슈 #37)
-- - 관리자 강사 신청 목록(API 104)이 실제로 신청한 강사만 보여 주도록, 신청할 때만 기록한다.
--   관리자가 기존 회원에게 직접 강사 권한을 준 경우(API 103)는 NULL 이라 신청 목록에 나오지 않는다.
-- - 이 컬럼을 처음 추가할 때만, 이미 있던 강사 정보는 모두 신청으로 만들어진 것이라 applied_at 을 created_at 으로 채운다.
-- 이미 컬럼이 있으면 아무것도 바꾸지 않는다. 다시 실행해도 결과가 같다.
-- 새로 DB를 만드는 사람은 schema.sql·sample-data.sql 에 이미 들어 있으므로 이 파일을 실행할 필요가 없다.

SET @db = DATABASE();
SET @need = (SELECT COUNT(*) = 0 FROM information_schema.columns
             WHERE table_schema = @db AND table_name = 'instructor_profile' AND column_name = 'applied_at');

SET @sql = IF(@need,
              'ALTER TABLE instructor_profile ADD COLUMN applied_at datetime DEFAULT NULL COMMENT ''강사 가입 신청 일시 (관리자가 직접 권한을 준 강사는 NULL, #37)'' AFTER approved_at',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@need, 'UPDATE instructor_profile SET applied_at = created_at WHERE applied_at IS NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
