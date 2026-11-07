-- housing_application 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS housing_application;

-- housing_application 테이블 생성 (청약 신청)
CREATE TABLE IF NOT EXISTS `housing_application` (
                                                     `housing_application_id` bigint NOT NULL AUTO_INCREMENT COMMENT '신청 ID',
                                                     `member_id` bigint NOT NULL COMMENT '신청자',
                                                     `housing_complex_id` bigint NOT NULL COMMENT '단지',
                                                     `receipt_no` varchar(20) NOT NULL COMMENT '접수번호',
    `homeless_years` tinyint NOT NULL COMMENT '입력한 무주택 기간(년)',
    `dependents` tinyint NOT NULL COMMENT '입력한 부양가족 수',
    `paid_count_snapshot` int NOT NULL COMMENT '신청 시점 납입회차',
    `homeless_score` tinyint NOT NULL COMMENT '무주택 점수 (만점 32)',
    `dependent_score` tinyint NOT NULL COMMENT '부양가족 점수 (만점 35)',
    `account_score` tinyint NOT NULL COMMENT '통장 점수 (만점 17)',
    `total_score` tinyint NOT NULL COMMENT '총 가점 (만점 84)',
    `result` varchar(10) DEFAULT NULL COMMENT 'WIN 당첨 / LOSE 낙첨. 발표 전 NULL',
    `result_rank` int DEFAULT NULL COMMENT '산정 순위',
    `applied_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청 일시',
    PRIMARY KEY (`housing_application_id`),
    UNIQUE KEY `uk_housing_application_receipt_no` (`receipt_no`),
    UNIQUE KEY `uk_housing_application_1` (`member_id`,`housing_complex_id`),
    KEY `idx_housing_application_1` (`housing_complex_id`,`total_score`),
    CONSTRAINT `fk_housing_application_housing_complex_id` FOREIGN KEY (`housing_complex_id`) REFERENCES `housing_complex` (`housing_complex_id`),
    CONSTRAINT `fk_housing_application_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='청약 신청';

SET FOREIGN_KEY_CHECKS = 1;
