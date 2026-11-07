-- season_turn 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS season_turn;

-- season_turn 테이블 생성 (시즌 턴)
CREATE TABLE IF NOT EXISTS `season_turn` (
                                             `season_turn_id` bigint NOT NULL AUTO_INCREMENT COMMENT '턴 ID',
                                             `trading_season_id` bigint NOT NULL COMMENT '시즌',
                                             `turn_no` tinyint NOT NULL COMMENT '턴 번호 1~12',
                                             `trade_date` date NOT NULL COMMENT '거래일 (일요일 제외)',
                                             `progressed_at` datetime DEFAULT NULL COMMENT '이 턴으로 넘긴 시각',
                                             `progressed_by` bigint DEFAULT NULL COMMENT '넘긴 관리자',
                                             PRIMARY KEY (`season_turn_id`),
    UNIQUE KEY `uk_season_turn_1` (`trading_season_id`,`turn_no`),
    KEY `fk_season_turn_progressed_by` (`progressed_by`),
    CONSTRAINT `fk_season_turn_progressed_by` FOREIGN KEY (`progressed_by`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_season_turn_trading_season_id` FOREIGN KEY (`trading_season_id`) REFERENCES `trading_season` (`trading_season_id`),
    CONSTRAINT `ck_season_turn_1` CHECK ((`turn_no` between 1 and 12))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='시즌 턴';

SET FOREIGN_KEY_CHECKS = 1;
