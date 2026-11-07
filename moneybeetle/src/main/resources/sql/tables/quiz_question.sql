-- quiz_question 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS quiz_question;

-- quiz_question 테이블 생성 (퀴즈 문항)
CREATE TABLE IF NOT EXISTS `quiz_question` (
                                               `quiz_question_id` bigint NOT NULL AUTO_INCREMENT COMMENT '문항 ID',
                                               `course_id` bigint NOT NULL COMMENT '강의',
                                               `quiz_type` varchar(10) NOT NULL COMMENT 'TEST 테스트 퀴즈 / MAIN 퀴즈',
    `question_type` varchar(10) NOT NULL COMMENT 'OX / CHOICE',
    `content` varchar(500) NOT NULL COMMENT '문제',
    `explanation` text NOT NULL COMMENT '해설',
    `sort_order` tinyint NOT NULL COMMENT '문항 순서',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`quiz_question_id`),
    KEY `idx_quiz_question_1` (`course_id`,`quiz_type`,`sort_order`),
    CONSTRAINT `fk_quiz_question_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`course_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='퀴즈 문항';

SET FOREIGN_KEY_CHECKS = 1;
