-- instructor_profile 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS instructor_profile;

-- instructor_profile 테이블 생성 (강사 정보)
CREATE TABLE IF NOT EXISTS `instructor_profile` (
                                                    `instructor_profile_id` bigint NOT NULL AUTO_INCREMENT COMMENT '강사 정보 ID',
                                                    `member_id` bigint NOT NULL COMMENT '강사 회원 (1:1)',
                                                    `display_name` varchar(30) NOT NULL COMMENT '강의에 표시할 이름',
    `field` varchar(100) DEFAULT NULL COMMENT '전문 분야 (예: 청약, ETF)',
    `approval_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REVOKED',
    `approved_by` bigint DEFAULT NULL COMMENT '승인한 관리자',
    `approved_at` datetime DEFAULT NULL COMMENT '승인 일시',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`instructor_profile_id`),
    UNIQUE KEY `uk_instructor_profile_member_id` (`member_id`),
    KEY `fk_instructor_profile_approved_by` (`approved_by`),
    CONSTRAINT `fk_instructor_profile_approved_by` FOREIGN KEY (`approved_by`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_instructor_profile_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='강사 정보';

SET FOREIGN_KEY_CHECKS = 1;
