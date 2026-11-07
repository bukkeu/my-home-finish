-- news 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS news;

-- news 테이블 생성 (가상 뉴스)
CREATE TABLE IF NOT EXISTS `news` (
                                      `news_id` bigint NOT NULL AUTO_INCREMENT COMMENT '뉴스 ID',
                                      `trading_season_id` bigint NOT NULL COMMENT '시즌',
                                      `turn_no` tinyint NOT NULL COMMENT '공개 턴',
                                      `title` varchar(100) NOT NULL COMMENT '제목',
    `content` text NOT NULL COMMENT '본문',
    `admin_memo` varchar(500) DEFAULT NULL COMMENT '설계 의도 메모 (관리자 전용)',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`news_id`),
    KEY `idx_news_1` (`trading_season_id`,`turn_no`),
    CONSTRAINT `fk_news_trading_season_id` FOREIGN KEY (`trading_season_id`) REFERENCES `trading_season` (`trading_season_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='가상 뉴스';

SET FOREIGN_KEY_CHECKS = 1;
