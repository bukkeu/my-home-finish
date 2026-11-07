-- stock 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS stock;

-- stock 테이블 생성 (가상 종목)
CREATE TABLE IF NOT EXISTS `stock` (
                                       `stock_id` bigint NOT NULL AUTO_INCREMENT COMMENT '종목 ID',
                                       `sector_id` bigint NOT NULL COMMENT '분야',
                                       `name` varchar(50) NOT NULL COMMENT '종목 이름',
    `start_price` int NOT NULL COMMENT '시작 가격',
    `intro` text COMMENT '회사 소개',
    `business` varchar(300) DEFAULT NULL COMMENT '주요 사업',
    `revenue` varchar(50) DEFAULT NULL COMMENT '가상 매출',
    `operating_profit` varchar(50) DEFAULT NULL COMMENT '가상 영업이익',
    `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '사용 여부',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`stock_id`),
    UNIQUE KEY `uk_stock_name` (`name`),
    KEY `fk_stock_sector_id` (`sector_id`),
    CONSTRAINT `fk_stock_sector_id` FOREIGN KEY (`sector_id`) REFERENCES `sector` (`sector_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='가상 종목';

SET FOREIGN_KEY_CHECKS = 1;
