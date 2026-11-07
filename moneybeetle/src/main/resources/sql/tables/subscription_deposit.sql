-- subscription_deposit 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS subscription_deposit;

-- subscription_deposit 테이블 생성 (청약 납입 내역)
CREATE TABLE IF NOT EXISTS `subscription_deposit` (
                                                      `subscription_deposit_id` bigint NOT NULL AUTO_INCREMENT COMMENT '납입 ID',
                                                      `subscription_account_id` bigint NOT NULL COMMENT '통장',
                                                      `paid_date` date NOT NULL COMMENT '납입일 (KST)',
                                                      `amount` int NOT NULL COMMENT '납입 캐시 (1회 1,000, 초안)',
                                                      `paid_count_after` int NOT NULL COMMENT '납입 후 회차',
                                                      `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
                                                      PRIMARY KEY (`subscription_deposit_id`),
    UNIQUE KEY `uk_subscription_deposit_1` (`subscription_account_id`,`paid_date`),
    CONSTRAINT `fk_subscription_deposit_subscription_account_id` FOREIGN KEY (`subscription_account_id`) REFERENCES `subscription_account` (`subscription_account_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='청약 납입 내역';

SET FOREIGN_KEY_CHECKS = 1;
