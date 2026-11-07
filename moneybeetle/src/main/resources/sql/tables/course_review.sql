-- course_review 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS course_review;

-- course_review 테이블 생성 (강의 검수 이력)
CREATE TABLE IF NOT EXISTS `course_review` (
                                               `course_review_id` bigint NOT NULL AUTO_INCREMENT COMMENT '검수 ID',
                                               `course_id` bigint NOT NULL COMMENT '강의',
                                               `admin_id` bigint NOT NULL COMMENT '검수한 관리자',
                                               `result` varchar(20) NOT NULL COMMENT 'APPROVED / REJECTED',
    `reason` varchar(500) DEFAULT NULL COMMENT '반려 사유 (반려 시 필수)',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`course_review_id`),
    KEY `fk_course_review_course_id` (`course_id`),
    KEY `fk_course_review_admin_id` (`admin_id`),
    CONSTRAINT `fk_course_review_admin_id` FOREIGN KEY (`admin_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_course_review_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`course_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='강의 검수 이력';

SET FOREIGN_KEY_CHECKS = 1;
