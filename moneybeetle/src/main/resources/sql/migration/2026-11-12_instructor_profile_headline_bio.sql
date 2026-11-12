USE money_beetle;

-- 강사 정보(instructor_profile)에 한 줄 소개(headline)와 강사 소개(bio)를 추가한다 (이슈 #365)
-- - 강사 마이페이지(목업 InstructorDashboard)의 "한 줄 소개"·"강사 소개" 칸을 저장하기 위한 컬럼이다. 둘 다 비워 둘 수 있다.
--   (headline 60자, bio 1,000자는 API 12-2 의 길이 제한과 같다)
-- 이미 컬럼이 있으면 아무것도 바꾸지 않는다. 다시 실행해도 결과가 같다.
-- 새로 DB를 만드는 사람은 schema.sql 에 이미 들어 있으므로 이 파일을 실행할 필요가 없다.

SET @db = DATABASE();

SET @need = (SELECT COUNT(*) = 0 FROM information_schema.columns
             WHERE table_schema = @db AND table_name = 'instructor_profile' AND column_name = 'headline');
SET @sql = IF(@need,
              'ALTER TABLE instructor_profile ADD COLUMN headline varchar(60) DEFAULT NULL COMMENT ''한 줄 소개 (강사 마이페이지, #365)'' AFTER display_name',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @need = (SELECT COUNT(*) = 0 FROM information_schema.columns
             WHERE table_schema = @db AND table_name = 'instructor_profile' AND column_name = 'bio');
SET @sql = IF(@need,
              'ALTER TABLE instructor_profile ADD COLUMN bio varchar(1000) DEFAULT NULL COMMENT ''강사 소개 (강사 마이페이지, #365)'' AFTER headline',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
