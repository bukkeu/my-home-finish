-- economic_term 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS economic_term;

-- economic_term 테이블 생성 (경제 용어)
CREATE TABLE IF NOT EXISTS `economic_term` (
                                               `economic_term_id` bigint NOT NULL AUTO_INCREMENT COMMENT '용어 ID',
                                               `term` varchar(50) NOT NULL COMMENT '용어',
    `description` varchar(500) NOT NULL COMMENT '뜻',
    `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '사용 여부',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`economic_term_id`),
    UNIQUE KEY `uk_economic_term_term` (`term`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='경제 용어';

SET FOREIGN_KEY_CHECKS = 1;
