-- ranking_award 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS ranking_award;

-- ranking_award 테이블 생성 (랭킹 수상)
CREATE TABLE IF NOT EXISTS `ranking_award` (
                                               `ranking_award_id` bigint NOT NULL AUTO_INCREMENT COMMENT '수상 ID',
                                               `trading_season_id` bigint NOT NULL COMMENT '시즌',
                                               `member_id` bigint NOT NULL COMMENT '회원',
                                               `ranking_type` varchar(10) NOT NULL COMMENT 'ASSET 순자산 / RETURN 수익률',
    `rank_no` tinyint NOT NULL COMMENT '순위 1~3',
    `badge_name` varchar(50) DEFAULT NULL COMMENT '뱃지·칭호 장식 이름',
    `awarded_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '지급 일시',
    PRIMARY KEY (`ranking_award_id`),
    UNIQUE KEY `uk_ranking_award_1` (`trading_season_id`,`member_id`,`ranking_type`),
    KEY `fk_ranking_award_member_id` (`member_id`),
    CONSTRAINT `fk_ranking_award_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_ranking_award_trading_season_id` FOREIGN KEY (`trading_season_id`) REFERENCES `trading_season` (`trading_season_id`),
    CONSTRAINT `ck_ranking_award_1` CHECK ((`rank_no` between 1 and 3))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='랭킹 수상';

SET FOREIGN_KEY_CHECKS = 1;
