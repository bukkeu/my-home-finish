-- category 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS category;

-- category 테이블 생성 (카테고리·소주제)
CREATE TABLE IF NOT EXISTS `category` (
                                          `category_id` bigint NOT NULL AUTO_INCREMENT COMMENT '카테고리 ID',
                                          `parent_id` bigint DEFAULT NULL COMMENT '상위 카테고리. NULL이면 최상위',
                                          `name` varchar(30) NOT NULL COMMENT '이름',
    `depth` tinyint NOT NULL DEFAULT '1' COMMENT '1 카테고리 / 2 소주제',
    `sort_order` int NOT NULL DEFAULT '0' COMMENT '정렬 순서',
    `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '사용 여부',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`category_id`),
    KEY `fk_category_parent_id` (`parent_id`),
    CONSTRAINT `fk_category_parent_id` FOREIGN KEY (`parent_id`) REFERENCES `category` (`category_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='카테고리·소주제';

SET FOREIGN_KEY_CHECKS = 1;
