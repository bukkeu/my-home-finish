-- cash_history 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS cash_history;

-- cash_history 테이블 생성 (캐시 내역)
CREATE TABLE IF NOT EXISTS `cash_history` (
                                              `cash_history_id` bigint NOT NULL AUTO_INCREMENT COMMENT '내역 ID',
                                              `member_id` bigint NOT NULL COMMENT '회원',
                                              `history_type` varchar(20) NOT NULL COMMENT 'EARN / USE / ADMIN_GIVE / ADMIN_TAKE',
    `source_type` varchar(30) NOT NULL COMMENT 'ATTENDANCE / COURSE_COMPLETE / LEVEL_UP / SEASON_JOIN / SUBSCRIPTION_DEPOSIT / ADMIN',
    `source_id` bigint DEFAULT NULL COMMENT '원인 데이터 ID (출석 ID, 강의 ID, 레벨 등)',
    `reward_rule_id` bigint DEFAULT NULL COMMENT '적용한 보상 규칙',
    `amount` bigint NOT NULL COMMENT '변동 금액 (사용·회수는 음수)',
    `balance_after` bigint NOT NULL COMMENT '변동 후 잔액',
    `reason` varchar(200) DEFAULT NULL COMMENT '관리자 지급·회수 사유 (필수)',
    `admin_id` bigint DEFAULT NULL COMMENT '처리한 관리자',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`cash_history_id`),
    UNIQUE KEY `uk_cash_history_1` (`member_id`,`source_type`,`source_id`),
    KEY `idx_cash_history_1` (`member_id`,`created_at`),
    KEY `fk_cash_history_reward_rule_id` (`reward_rule_id`),
    KEY `fk_cash_history_admin_id` (`admin_id`),
    CONSTRAINT `fk_cash_history_admin_id` FOREIGN KEY (`admin_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_cash_history_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_cash_history_reward_rule_id` FOREIGN KEY (`reward_rule_id`) REFERENCES `reward_rule` (`reward_rule_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='캐시 내역';

SET FOREIGN_KEY_CHECKS = 1;
