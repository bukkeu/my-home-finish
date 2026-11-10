USE money_beetle;

-- 인덱스 점검 결과 추가하는 인덱스 (이슈 #87, 신아림 구역: 회원·강의·문의·운영)
-- - member    idx_member_1 (role, status)          : 관리자 대시보드 회원·강사 수 집계(countByRole·countByRoleAndStatus), 회원·강사 목록 필터
-- - member    idx_member_2 (status, withdrawn_at)  : 탈퇴 회원 개인정보 정리 대상 조회(findPurgeTargets)
-- - course    idx_course_2 (status, submitted_at)  : 관리자 강의 목록 상태 필터 + 검수 요청 최신순 정렬(searchForAdmin)
-- - admin_log idx_admin_log_3 (created_at)         : 관리자 작업 기록 필터 없이 최신순 조회(AdminLogRepository.search)
-- 이미 같은 이름의 인덱스가 있으면 건너뛴다. 다시 실행해도 결과가 같다.
-- 새로 DB를 만드는 사람은 schema.sql 에 이미 들어 있으므로 이 파일을 실행할 필요가 없다.

SET @db = DATABASE();

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics
               WHERE table_schema = @db AND table_name = 'member' AND index_name = 'idx_member_1') = 0,
              'ALTER TABLE member ADD KEY idx_member_1 (role, status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics
               WHERE table_schema = @db AND table_name = 'member' AND index_name = 'idx_member_2') = 0,
              'ALTER TABLE member ADD KEY idx_member_2 (status, withdrawn_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics
               WHERE table_schema = @db AND table_name = 'course' AND index_name = 'idx_course_2') = 0,
              'ALTER TABLE course ADD KEY idx_course_2 (status, submitted_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics
               WHERE table_schema = @db AND table_name = 'admin_log' AND index_name = 'idx_admin_log_3') = 0,
              'ALTER TABLE admin_log ADD KEY idx_admin_log_3 (created_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
