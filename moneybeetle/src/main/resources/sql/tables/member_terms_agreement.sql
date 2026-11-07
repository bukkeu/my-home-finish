-- member_terms_agreement 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS member_terms_agreement;

-- member_terms_agreement 테이블 생성 (약관 동의)
CREATE TABLE IF NOT EXISTS `member_terms_agreement` (
                                                        `member_terms_agreement_id` bigint NOT NULL AUTO_INCREMENT COMMENT '동의 ID',
                                                        `member_id` bigint NOT NULL COMMENT '회원',
                                                        `terms_id` bigint NOT NULL COMMENT '약관',
                                                        `is_agreed` tinyint(1) NOT NULL COMMENT '동의 여부',
    `agreed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '동의 일시',
    PRIMARY KEY (`member_terms_agreement_id`),
    UNIQUE KEY `uk_member_terms_agreement_1` (`member_id`,`terms_id`),
    KEY `fk_member_terms_agreement_terms_id` (`terms_id`),
    CONSTRAINT `fk_member_terms_agreement_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_member_terms_agreement_terms_id` FOREIGN KEY (`terms_id`) REFERENCES `terms` (`terms_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='약관 동의';

SET FOREIGN_KEY_CHECKS = 1;
