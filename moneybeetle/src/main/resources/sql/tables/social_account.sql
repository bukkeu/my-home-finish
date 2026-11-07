-- social_account 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS social_account;

-- social_account 테이블 생성 (간편 로그인 연결)
CREATE TABLE IF NOT EXISTS `social_account` (
                                                `social_account_id` bigint NOT NULL AUTO_INCREMENT COMMENT '연결 ID',
                                                `member_id` bigint NOT NULL COMMENT '회원',
                                                `provider` varchar(20) NOT NULL COMMENT 'GOOGLE / KAKAO',
    `provider_user_id` varchar(100) NOT NULL COMMENT '제공자 쪽 사용자 ID',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`social_account_id`),
    UNIQUE KEY `uk_social_account_1` (`provider`,`provider_user_id`),
    KEY `fk_social_account_member_id` (`member_id`),
    CONSTRAINT `fk_social_account_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='간편 로그인 연결';

SET FOREIGN_KEY_CHECKS = 1;
