-- course 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS course;

-- course 테이블 생성 (강의)
CREATE TABLE IF NOT EXISTS `course` (
                                        `course_id` bigint NOT NULL AUTO_INCREMENT COMMENT '강의 ID',
                                        `instructor_id` bigint NOT NULL COMMENT '강사',
                                        `category_id` bigint NOT NULL COMMENT '소주제',
                                        `title` varchar(100) NOT NULL COMMENT '강의 제목',
    `description` text COMMENT '강의 소개',
    `thumbnail_url` varchar(500) DEFAULT NULL COMMENT '썸네일',
    `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT 작성 중 / REVIEW 검수 중 / APPROVED 허가 / REJECTED 반려',
    `is_public` tinyint(1) NOT NULL DEFAULT '0' COMMENT '공개 여부 (허가 후에도 비공개 가능)',
    `info_base_date` date DEFAULT NULL COMMENT '제도 정보 기준일 (NFR-15)',
    `info_source` varchar(200) DEFAULT NULL COMMENT '제도 정보 출처',
    `submitted_at` datetime DEFAULT NULL COMMENT '검수 요청 일시',
    `approved_at` datetime DEFAULT NULL COMMENT '허가 일시',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    `deleted_at` datetime DEFAULT NULL COMMENT '삭제 일시 (Soft Delete)',
    PRIMARY KEY (`course_id`),
    KEY `idx_course_1` (`category_id`,`status`,`is_public`),
    KEY `fk_course_instructor_id` (`instructor_id`),
    CONSTRAINT `fk_course_category_id` FOREIGN KEY (`category_id`) REFERENCES `category` (`category_id`),
    CONSTRAINT `fk_course_instructor_id` FOREIGN KEY (`instructor_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='강의';

SET FOREIGN_KEY_CHECKS = 1;
