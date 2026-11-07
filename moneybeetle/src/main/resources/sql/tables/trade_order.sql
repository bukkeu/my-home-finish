-- trade_order 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS trade_order;

-- trade_order 테이블 생성 (거래 내역)
CREATE TABLE IF NOT EXISTS `trade_order` (
                                             `trade_order_id` bigint NOT NULL AUTO_INCREMENT COMMENT '주문 ID',
                                             `season_participant_id` bigint NOT NULL COMMENT '참가자',
                                             `stock_id` bigint NOT NULL COMMENT '종목',
                                             `turn_no` tinyint NOT NULL COMMENT '체결 턴',
                                             `order_type` varchar(10) NOT NULL COMMENT 'BUY / SELL',
    `quantity` int NOT NULL COMMENT '수량 (1 이상 정수)',
    `price` int NOT NULL COMMENT '체결가 (그 턴의 고정 가격)',
    `amount` bigint NOT NULL COMMENT '체결 금액 = 수량 × 가격',
    `trade_reason_id` bigint NOT NULL COMMENT '매매 이유',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`trade_order_id`),
    KEY `idx_trade_order_1` (`season_participant_id`,`turn_no`),
    KEY `fk_trade_order_stock_id` (`stock_id`),
    KEY `fk_trade_order_trade_reason_id` (`trade_reason_id`),
    CONSTRAINT `fk_trade_order_season_participant_id` FOREIGN KEY (`season_participant_id`) REFERENCES `season_participant` (`season_participant_id`),
    CONSTRAINT `fk_trade_order_stock_id` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`stock_id`),
    CONSTRAINT `fk_trade_order_trade_reason_id` FOREIGN KEY (`trade_reason_id`) REFERENCES `trade_reason` (`trade_reason_id`),
    CONSTRAINT `ck_trade_order_1` CHECK ((`quantity` > 0)),
    CONSTRAINT `ck_trade_order_2` CHECK ((`price` > 0))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='거래 내역';

SET FOREIGN_KEY_CHECKS = 1;
