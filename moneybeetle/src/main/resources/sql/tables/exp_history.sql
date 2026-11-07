-- exp_history 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS exp_history;

-- exp_history 테이블 생성 (경험치 내역)
CREATE TABLE IF NOT EXISTS `exp_history` (
                                             `exp_history_id` bigint NOT NULL AUTO_INCREMENT COMMENT '내역 ID',
                                             `member_id` bigint NOT NULL COMMENT '회원',
                                             `source_type` varchar(30) NOT NULL COMMENT 'COURSE_COMPLETE / QUIZ_PERFECT',
    `source_id` bigint NOT NULL COMMENT '원인 강의 ID',
    `reward_rule_id` bigint DEFAULT NULL COMMENT '적용한 보상 규칙',
    `amount` int NOT NULL COMMENT '지급 경험치',
    `exp_after` int NOT NULL COMMENT '지급 후 누적 경험치',
    `level_after` tinyint NOT NULL COMMENT '지급 후 레벨',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`exp_history_id`),
    UNIQUE KEY `uk_exp_history_1` (`member_id`,`source_type`,`source_id`),
    KEY `idx_exp_history_1` (`member_id`,`created_at`),
    KEY `fk_exp_history_reward_rule_id` (`reward_rule_id`),
    CONSTRAINT `fk_exp_history_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_exp_history_reward_rule_id` FOREIGN KEY (`reward_rule_id`) REFERENCES `reward_rule` (`reward_rule_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='경험치 내역';

SET FOREIGN_KEY_CHECKS = 1;
