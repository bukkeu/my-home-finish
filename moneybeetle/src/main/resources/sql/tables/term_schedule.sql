-- term_schedule 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS term_schedule;

-- term_schedule 테이블 생성 (오늘의 경제 용어)
CREATE TABLE IF NOT EXISTS `term_schedule` (
                                               `term_schedule_id` bigint NOT NULL AUTO_INCREMENT COMMENT '일정 ID',
                                               `show_date` date NOT NULL COMMENT '노출일 (KST)',
                                               `economic_term_id` bigint NOT NULL COMMENT '용어',
                                               `sort_order` tinyint NOT NULL COMMENT '순서 1~5',
                                               `is_auto` tinyint(1) NOT NULL DEFAULT '0' COMMENT '자동 선정 여부 (관리자 미지정일)',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`term_schedule_id`),
    UNIQUE KEY `uk_term_schedule_1` (`show_date`,`sort_order`),
    UNIQUE KEY `uk_term_schedule_2` (`show_date`,`economic_term_id`),
    KEY `fk_term_schedule_economic_term_id` (`economic_term_id`),
    CONSTRAINT `fk_term_schedule_economic_term_id` FOREIGN KEY (`economic_term_id`) REFERENCES `economic_term` (`economic_term_id`),
    CONSTRAINT `ck_term_schedule_1` CHECK ((`sort_order` between 1 and 5))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='오늘의 경제 용어';

SET FOREIGN_KEY_CHECKS = 1;
