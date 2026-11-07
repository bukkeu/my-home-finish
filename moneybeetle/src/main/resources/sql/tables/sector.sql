-- sector 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS sector;

-- sector 테이블 생성 (분야)
CREATE TABLE IF NOT EXISTS `sector` (
                                        `sector_id` bigint NOT NULL AUTO_INCREMENT COMMENT '분야 ID',
                                        `name` varchar(30) NOT NULL COMMENT '분야 이름 (반도체, 바이오 등)',
    `sort_order` int NOT NULL DEFAULT '0' COMMENT '정렬 순서',
    `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '사용 여부',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`sector_id`),
    UNIQUE KEY `uk_sector_name` (`name`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='분야';

SET FOREIGN_KEY_CHECKS = 1;
