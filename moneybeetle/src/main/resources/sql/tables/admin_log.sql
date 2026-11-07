-- admin_log 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS admin_log;

-- admin_log 테이블 생성 (관리자 작업 기록)
CREATE TABLE IF NOT EXISTS `admin_log` (
                                           `admin_log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '기록 ID',
                                           `admin_id` bigint NOT NULL COMMENT '작업한 관리자',
                                           `action` varchar(50) NOT NULL COMMENT 'TURN_ADVANCE / SETTLE / RANKING_PUBLISH / CASH_GIVE 등',
    `target_type` varchar(30) NOT NULL COMMENT '대상 종류 (SEASON, MEMBER, COURSE …)',
    `target_id` bigint DEFAULT NULL COMMENT '대상 ID',
    `detail` json DEFAULT NULL COMMENT '변경 전·후 값',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`admin_log_id`),
    KEY `idx_admin_log_1` (`target_type`,`target_id`),
    KEY `idx_admin_log_2` (`admin_id`,`created_at`),
    CONSTRAINT `fk_admin_log_admin_id` FOREIGN KEY (`admin_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자 작업 기록';

SET FOREIGN_KEY_CHECKS = 1;
