-- password_reset_token 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS password_reset_token;

-- password_reset_token 테이블 생성 (비밀번호 재설정 인증)
CREATE TABLE IF NOT EXISTS `password_reset_token` (
                                                      `password_reset_token_id` bigint NOT NULL AUTO_INCREMENT COMMENT '토큰 ID',
                                                      `member_id` bigint NOT NULL COMMENT '회원',
                                                      `token_hash` varchar(255) NOT NULL COMMENT '인증 토큰 해시',
    `expires_at` datetime NOT NULL COMMENT '만료 시각 (발급 + 10분)',
    `used_at` datetime DEFAULT NULL COMMENT '사용 시각. 사용하면 재사용 불가',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`password_reset_token_id`),
    UNIQUE KEY `uk_password_reset_token_token_hash` (`token_hash`),
    KEY `fk_password_reset_token_member_id` (`member_id`),
    CONSTRAINT `fk_password_reset_token_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='비밀번호 재설정 인증';

SET FOREIGN_KEY_CHECKS = 1;
