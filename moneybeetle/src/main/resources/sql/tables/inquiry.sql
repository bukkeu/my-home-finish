-- inquiry 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS inquiry;

-- inquiry 테이블 생성 (문의글)
CREATE TABLE IF NOT EXISTS `inquiry` (
                                         `inquiry_id` bigint NOT NULL AUTO_INCREMENT COMMENT '문의 ID',
                                         `member_id` bigint NOT NULL COMMENT '작성자',
                                         `course_id` bigint DEFAULT NULL COMMENT '문의한 강의. NULL이면 사이트 문의',
                                         `category` varchar(30) DEFAULT NULL COMMENT '사이트 문의 분류 (계정·캐시, 모의 청약 등)',
    `title` varchar(50) NOT NULL COMMENT '제목 50자',
    `content` varchar(1000) NOT NULL COMMENT '내용 1,000자',
    `status` varchar(20) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING 답변 대기 / ANSWERED 답변 완료',
    `is_hidden` tinyint(1) NOT NULL DEFAULT '0' COMMENT '관리자 숨김 여부',
    `hidden_by` bigint DEFAULT NULL COMMENT '숨김 처리한 관리자',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    `deleted_at` datetime DEFAULT NULL COMMENT '삭제 일시 (Soft Delete)',
    PRIMARY KEY (`inquiry_id`),
    KEY `idx_inquiry_1` (`course_id`,`created_at`),
    KEY `idx_inquiry_2` (`member_id`,`created_at`),
    KEY `fk_inquiry_hidden_by` (`hidden_by`),
    CONSTRAINT `fk_inquiry_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`course_id`),
    CONSTRAINT `fk_inquiry_hidden_by` FOREIGN KEY (`hidden_by`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_inquiry_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='문의글';

SET FOREIGN_KEY_CHECKS = 1;
