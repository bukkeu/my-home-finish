-- stock_price 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS stock_price;

-- stock_price 테이블 생성 (턴별 가격)
CREATE TABLE IF NOT EXISTS `stock_price` (
                                             `stock_price_id` bigint NOT NULL AUTO_INCREMENT COMMENT '가격 ID',
                                             `trading_season_id` bigint NOT NULL COMMENT '시즌',
                                             `stock_id` bigint NOT NULL COMMENT '종목',
                                             `turn_no` tinyint NOT NULL COMMENT '턴 1~12',
                                             `price` int NOT NULL COMMENT '이 턴의 고정 가격 (1 이상)',
                                             `change_rate` decimal(6,2) DEFAULT NULL COMMENT '지난 턴 대비 변동률(%)',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`stock_price_id`),
    UNIQUE KEY `uk_stock_price_1` (`trading_season_id`,`stock_id`,`turn_no`),
    KEY `fk_stock_price_stock_id` (`stock_id`),
    CONSTRAINT `fk_stock_price_stock_id` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`stock_id`),
    CONSTRAINT `fk_stock_price_trading_season_id` FOREIGN KEY (`trading_season_id`) REFERENCES `trading_season` (`trading_season_id`),
    CONSTRAINT `ck_stock_price_1` CHECK ((`price` > 0)),
    CONSTRAINT `ck_stock_price_2` CHECK ((`turn_no` between 1 and 12))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='턴별 가격';

SET FOREIGN_KEY_CHECKS = 1;
