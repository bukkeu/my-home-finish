-- reward_rule 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS reward_rule;

-- reward_rule 테이블 생성 (보상 규칙)
CREATE TABLE IF NOT EXISTS `reward_rule` (
                                             `reward_rule_id` bigint NOT NULL AUTO_INCREMENT COMMENT '규칙 ID',
                                             `code` varchar(30) NOT NULL COMMENT 'COURSE_COMPLETE / QUIZ_PERFECT / ATTENDANCE_STREAK',
    `name` varchar(50) NOT NULL COMMENT '규칙 이름',
    `cash_amount` int NOT NULL DEFAULT '0' COMMENT '지급 캐시',
    `exp_amount` int NOT NULL DEFAULT '0' COMMENT '지급 경험치',
    `description` varchar(200) DEFAULT NULL COMMENT '지급 조건 설명',
    `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '사용 여부',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`reward_rule_id`),
    UNIQUE KEY `uk_reward_rule_code` (`code`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='보상 규칙';

SET FOREIGN_KEY_CHECKS = 1;
