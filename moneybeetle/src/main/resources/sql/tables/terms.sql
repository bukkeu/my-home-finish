-- terms 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS terms;

-- terms 테이블 생성 (약관)
CREATE TABLE IF NOT EXISTS `terms` (
                                       `terms_id` bigint NOT NULL AUTO_INCREMENT COMMENT '약관 ID',
                                       `title` varchar(100) NOT NULL COMMENT '약관 이름',
    `content` text NOT NULL COMMENT '약관 본문',
    `is_required` tinyint(1) NOT NULL COMMENT '필수 약관 여부',
    `version` varchar(20) NOT NULL COMMENT '버전 (예: 1.0)',
    `effective_at` datetime NOT NULL COMMENT '시행 일시',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`terms_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='약관';

SET FOREIGN_KEY_CHECKS = 1;
