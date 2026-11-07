-- level_policy 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS level_policy;

-- level_policy 테이블 생성 (레벨 정책)
CREATE TABLE IF NOT EXISTS `level_policy` (
                                              `level_policy_id` bigint NOT NULL AUTO_INCREMENT COMMENT '레벨 정책 ID',
                                              `level` tinyint NOT NULL COMMENT '레벨 1~20',
                                              `required_exp` int NOT NULL COMMENT '이 레벨이 되기 위한 누적 경험치 (150 XP 간격, 초안)',
                                              `title` varchar(20) NOT NULL COMMENT '칭호: 텐트 / 원룸 / 투룸 / 아파트 / 내 집',
    `levelup_cash` int NOT NULL DEFAULT '0' COMMENT '레벨업 캐시 (레벨 × 1,000, 초안)',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`level_policy_id`),
    UNIQUE KEY `uk_level_policy_level` (`level`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='레벨 정책';

SET FOREIGN_KEY_CHECKS = 1;
