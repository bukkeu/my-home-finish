-- holding 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS holding;

-- holding 테이블 생성 (보유 종목)
CREATE TABLE IF NOT EXISTS `holding` (
                                         `holding_id` bigint NOT NULL AUTO_INCREMENT COMMENT '보유 ID',
                                         `season_participant_id` bigint NOT NULL COMMENT '참가자',
                                         `stock_id` bigint NOT NULL COMMENT '종목',
                                         `quantity` int NOT NULL DEFAULT '0' COMMENT '보유 수량 (0 이상)',
                                         `avg_price` int NOT NULL DEFAULT '0' COMMENT '평균 매수 단가',
                                         `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
                                         PRIMARY KEY (`holding_id`),
    UNIQUE KEY `uk_holding_1` (`season_participant_id`,`stock_id`),
    KEY `fk_holding_stock_id` (`stock_id`),
    CONSTRAINT `fk_holding_season_participant_id` FOREIGN KEY (`season_participant_id`) REFERENCES `season_participant` (`season_participant_id`),
    CONSTRAINT `fk_holding_stock_id` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`stock_id`),
    CONSTRAINT `ck_holding_1` CHECK ((`quantity` >= 0))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='보유 종목';

SET FOREIGN_KEY_CHECKS = 1;
