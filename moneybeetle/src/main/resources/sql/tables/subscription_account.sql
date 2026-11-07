-- subscription_account 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS subscription_account;

-- subscription_account 테이블 생성 (청약통장)
CREATE TABLE IF NOT EXISTS `subscription_account` (
                                                      `subscription_account_id` bigint NOT NULL AUTO_INCREMENT COMMENT '통장 ID',
                                                      `member_id` bigint NOT NULL COMMENT '회원 (1:1)',
                                                      `balance` bigint NOT NULL DEFAULT '0' COMMENT '통장 잔액',
                                                      `paid_count` int NOT NULL DEFAULT '0' COMMENT '납입회차',
                                                      `last_paid_date` date DEFAULT NULL COMMENT '마지막 납입일 (KST)',
                                                      `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
                                                      `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
                                                      PRIMARY KEY (`subscription_account_id`),
    UNIQUE KEY `uk_subscription_account_member_id` (`member_id`),
    CONSTRAINT `fk_subscription_account_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='청약통장';

SET FOREIGN_KEY_CHECKS = 1;
