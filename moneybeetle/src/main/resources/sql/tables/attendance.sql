-- attendance 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS attendance;

-- attendance 테이블 생성 (출석)
CREATE TABLE IF NOT EXISTS `attendance` (
                                            `attendance_id` bigint NOT NULL AUTO_INCREMENT COMMENT '출석 ID',
                                            `member_id` bigint NOT NULL COMMENT '회원',
                                            `attend_date` date NOT NULL COMMENT '출석일 (KST)',
                                            `streak_count` int NOT NULL COMMENT '이날 기준 연속 출석 일수',
                                            `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
                                            PRIMARY KEY (`attendance_id`),
    UNIQUE KEY `uk_attendance_1` (`member_id`,`attend_date`),
    CONSTRAINT `fk_attendance_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='출석';

SET FOREIGN_KEY_CHECKS = 1;
