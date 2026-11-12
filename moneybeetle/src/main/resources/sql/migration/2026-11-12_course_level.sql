USE money_beetle;

-- 강의 난이도(level)를 course 에 추가한다 (이슈 #369)
-- - 강사 강의 관리 표(API 72)와 강의 생성·수정(API 73~75)이 난이도를 쓴다.
-- - 값 : BEGINNER 입문 / ELEMENTARY 초급 / INTERMEDIATE 중급 / ADVANCED 고급. 이미 있는 강의는 BEGINNER(입문)가 된다.
-- 이미 컬럼이 있으면 아무것도 바꾸지 않는다. 다시 실행해도 결과가 같다.
-- 새로 DB를 만드는 사람은 schema.sql 에 이미 들어 있으므로 이 파일을 실행할 필요가 없다.

SET @db = DATABASE();
SET @need = (SELECT COUNT(*) = 0 FROM information_schema.columns
             WHERE table_schema = @db AND table_name = 'course' AND column_name = 'level');

SET @sql = IF(@need,
              'ALTER TABLE course ADD COLUMN level varchar(20) NOT NULL DEFAULT ''BEGINNER'' COMMENT ''난이도 BEGINNER 입문 / ELEMENTARY 초급 / INTERMEDIATE 중급 / ADVANCED 고급'' AFTER description',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
