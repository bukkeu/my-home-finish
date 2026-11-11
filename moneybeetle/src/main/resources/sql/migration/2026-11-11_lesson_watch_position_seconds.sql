USE money_beetle;

-- 영상별 이어보기 위치(position_seconds)를 시청 기록(lesson_watch)에 추가한다 (이슈 #71)
-- - 지금까지는 강의마다 마지막으로 본 영상 하나의 위치(learning_progress.last_position_seconds)만 남아서,
--   같은 강의의 다른 영상을 본 뒤 마이페이지에서 앞 영상을 이어보기 하면 처음부터 재생됐다.
-- - 이 컬럼을 처음 추가할 때만, 각 강의의 마지막 영상 위치를 그 영상의 시청 기록으로 옮긴다.
--   그 전에 덮어써져 사라진 다른 영상의 위치는 되살릴 수 없어 0(처음부터)으로 둔다.
-- 이미 컬럼이 있으면 아무것도 바꾸지 않는다. 다시 실행해도 결과가 같다.
-- 새로 DB를 만드는 사람은 schema.sql 에 이미 들어 있으므로 이 파일을 실행할 필요가 없다.

SET @db = DATABASE();
SET @need = (SELECT COUNT(*) = 0 FROM information_schema.columns
             WHERE table_schema = @db AND table_name = 'lesson_watch' AND column_name = 'position_seconds');

SET @sql = IF(@need,
              'ALTER TABLE lesson_watch ADD COLUMN position_seconds int NOT NULL DEFAULT ''0'' COMMENT ''이 영상의 마지막 재생 위치(초, 이어보기 #71)'' AFTER watched_seconds',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@need,
              'UPDATE lesson_watch lw JOIN learning_progress lp ON lp.member_id = lw.member_id AND lp.last_lesson_id = lw.lesson_id SET lw.position_seconds = lp.last_position_seconds',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
