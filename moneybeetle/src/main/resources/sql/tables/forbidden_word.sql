-- forbidden_word 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS forbidden_word;

-- forbidden_word 테이블 생성 (금칙어)
CREATE TABLE IF NOT EXISTS `forbidden_word` (
                                                `forbidden_word_id` bigint NOT NULL AUTO_INCREMENT COMMENT '금칙어 ID',
                                                `word` varchar(50) NOT NULL COMMENT '단어',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`forbidden_word_id`),
    UNIQUE KEY `uk_forbidden_word_word` (`word`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='금칙어';

SET FOREIGN_KEY_CHECKS = 1;
