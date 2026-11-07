-- system_setting 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS system_setting;

-- system_setting 테이블 생성 (서비스 설정)
CREATE TABLE IF NOT EXISTS `system_setting` (
                                                `system_setting_id` bigint NOT NULL AUTO_INCREMENT COMMENT '설정 ID',
                                                `setting_key` varchar(50) NOT NULL COMMENT '키 (UNLOCK_LEVEL_TRADING, QUIZ_PASS_SCORE …)',
    `setting_value` varchar(200) NOT NULL COMMENT '값',
    `description` varchar(200) DEFAULT NULL COMMENT '설명',
    `updated_by` bigint DEFAULT NULL COMMENT '마지막으로 바꾼 관리자',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`system_setting_id`),
    UNIQUE KEY `uk_system_setting_setting_key` (`setting_key`),
    KEY `fk_system_setting_updated_by` (`updated_by`),
    CONSTRAINT `fk_system_setting_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='서비스 설정';

SET FOREIGN_KEY_CHECKS = 1;
