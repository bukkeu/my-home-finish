-- news_tag 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS news_tag;

-- news_tag 테이블 생성 (뉴스 관련 분야·종목)
CREATE TABLE IF NOT EXISTS `news_tag` (
                                          `news_tag_id` bigint NOT NULL AUTO_INCREMENT COMMENT '연결 ID',
                                          `news_id` bigint NOT NULL COMMENT '뉴스',
                                          `sector_id` bigint DEFAULT NULL COMMENT '관련 분야',
                                          `stock_id` bigint DEFAULT NULL COMMENT '관련 종목',
                                          PRIMARY KEY (`news_tag_id`),
    KEY `fk_news_tag_news_id` (`news_id`),
    KEY `fk_news_tag_sector_id` (`sector_id`),
    KEY `fk_news_tag_stock_id` (`stock_id`),
    CONSTRAINT `fk_news_tag_news_id` FOREIGN KEY (`news_id`) REFERENCES `news` (`news_id`),
    CONSTRAINT `fk_news_tag_sector_id` FOREIGN KEY (`sector_id`) REFERENCES `sector` (`sector_id`),
    CONSTRAINT `fk_news_tag_stock_id` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`stock_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='뉴스 관련 분야·종목';

SET FOREIGN_KEY_CHECKS = 1;
