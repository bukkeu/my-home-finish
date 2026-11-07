-- trading_season 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS trading_season;

-- trading_season 테이블 생성 (투자 시즌)
CREATE TABLE IF NOT EXISTS `trading_season` (
                                                `trading_season_id` bigint NOT NULL AUTO_INCREMENT COMMENT '시즌 ID',
                                                `name` varchar(50) NOT NULL COMMENT '시즌 이름 (예: 시즌 7)',
    `start_date` date NOT NULL COMMENT '시작일 (월요일)',
    `end_date` date NOT NULL COMMENT '종료일 (시작 + 13일, 랭킹 공개일)',
    `status` varchar(20) NOT NULL DEFAULT 'READY' COMMENT 'READY / RUNNING / HOLIDAY / SETTLING / ENDED',
    `current_turn` tinyint NOT NULL DEFAULT '0' COMMENT '현재 턴 0~12',
    `total_turns` tinyint NOT NULL DEFAULT '12' COMMENT '전체 턴',
    `unlock_level` tinyint NOT NULL DEFAULT '8' COMMENT '참가 해금 레벨',
    `carryover_rate` decimal(5,2) NOT NULL DEFAULT '30.00' COMMENT '이월 비율(%)',
    `carryover_cap` bigint DEFAULT NULL COMMENT '이월 상한 (미정)',
    `settled_at` datetime DEFAULT NULL COMMENT '정산 일시',
    `ranking_published_at` datetime DEFAULT NULL COMMENT '랭킹 공개 일시',
    `created_by` bigint NOT NULL COMMENT '만든 관리자',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`trading_season_id`),
    KEY `fk_trading_season_created_by` (`created_by`),
    CONSTRAINT `fk_trading_season_created_by` FOREIGN KEY (`created_by`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='투자 시즌';

SET FOREIGN_KEY_CHECKS = 1;
