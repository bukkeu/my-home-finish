-- season_participant 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS season_participant;

-- season_participant 테이블 생성 (시즌 참가자)
CREATE TABLE IF NOT EXISTS `season_participant` (
                                                    `season_participant_id` bigint NOT NULL AUTO_INCREMENT COMMENT '참가 ID',
                                                    `trading_season_id` bigint NOT NULL COMMENT '시즌',
                                                    `member_id` bigint NOT NULL COMMENT '회원',
                                                    `saved_cash` bigint NOT NULL COMMENT '참가 때 가져온 캐시',
                                                    `carryover_in` bigint NOT NULL DEFAULT '0' COMMENT '지난 시즌 이월금',
                                                    `invest_amount` bigint NOT NULL COMMENT '투자 가능 금액 = 캐시 + 이월금 (변경 불가)',
                                                    `cash` bigint NOT NULL COMMENT '현재 보유 현금',
                                                    `final_net_asset` bigint DEFAULT NULL COMMENT '정산 순자산',
                                                    `return_rate` decimal(7,2) DEFAULT NULL COMMENT '정산 수익률(%)',
    `asset_rank` int DEFAULT NULL COMMENT '순자산 순위',
    `return_rank` int DEFAULT NULL COMMENT '수익률 순위',
    `carryover_out` bigint NOT NULL DEFAULT '0' COMMENT '다음 시즌 이월금',
    `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '참가 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`season_participant_id`),
    UNIQUE KEY `uk_season_participant_1` (`trading_season_id`,`member_id`),
    KEY `idx_season_participant_1` (`trading_season_id`,`final_net_asset`),
    KEY `idx_season_participant_2` (`trading_season_id`,`return_rate`),
    KEY `fk_season_participant_member_id` (`member_id`),
    CONSTRAINT `fk_season_participant_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_season_participant_trading_season_id` FOREIGN KEY (`trading_season_id`) REFERENCES `trading_season` (`trading_season_id`),
    CONSTRAINT `ck_season_participant_1` CHECK ((`cash` >= 0))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='시즌 참가자';

SET FOREIGN_KEY_CHECKS = 1;
