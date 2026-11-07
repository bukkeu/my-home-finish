-- inquiry_reply 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS inquiry_reply;

-- inquiry_reply 테이블 생성 (문의 답글)
CREATE TABLE IF NOT EXISTS `inquiry_reply` (
                                               `inquiry_reply_id` bigint NOT NULL AUTO_INCREMENT COMMENT '답글 ID',
                                               `inquiry_id` bigint NOT NULL COMMENT '문의',
                                               `writer_id` bigint NOT NULL COMMENT '작성자 (강사·관리자·질문자)',
                                               `content` varchar(1000) NOT NULL COMMENT '내용',
    `is_hidden` tinyint(1) NOT NULL DEFAULT '0' COMMENT '관리자 숨김 여부',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    `deleted_at` datetime DEFAULT NULL COMMENT '삭제 일시 (Soft Delete)',
    PRIMARY KEY (`inquiry_reply_id`),
    KEY `fk_inquiry_reply_inquiry_id` (`inquiry_id`),
    KEY `fk_inquiry_reply_writer_id` (`writer_id`),
    CONSTRAINT `fk_inquiry_reply_inquiry_id` FOREIGN KEY (`inquiry_id`) REFERENCES `inquiry` (`inquiry_id`),
    CONSTRAINT `fk_inquiry_reply_writer_id` FOREIGN KEY (`writer_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='문의 답글';

SET FOREIGN_KEY_CHECKS = 1;
