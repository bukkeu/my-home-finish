-- scrap 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS scrap;

-- scrap 테이블 생성 (강의 스크랩)
CREATE TABLE IF NOT EXISTS `scrap` (
                                       `scrap_id` bigint NOT NULL AUTO_INCREMENT COMMENT '스크랩 ID',
                                       `member_id` bigint NOT NULL COMMENT '회원',
                                       `course_id` bigint NOT NULL COMMENT '강의',
                                       `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
                                       PRIMARY KEY (`scrap_id`),
    UNIQUE KEY `uk_scrap_1` (`member_id`,`course_id`),
    KEY `fk_scrap_course_id` (`course_id`),
    CONSTRAINT `fk_scrap_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`course_id`),
    CONSTRAINT `fk_scrap_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='강의 스크랩';

SET FOREIGN_KEY_CHECKS = 1;
