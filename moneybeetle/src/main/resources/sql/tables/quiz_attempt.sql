-- quiz_attempt 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS quiz_attempt;

-- quiz_attempt 테이블 생성 (퀴즈 응시)
CREATE TABLE IF NOT EXISTS `quiz_attempt` (
                                              `quiz_attempt_id` bigint NOT NULL AUTO_INCREMENT COMMENT '응시 ID',
                                              `member_id` bigint NOT NULL COMMENT '응시자',
                                              `course_id` bigint NOT NULL COMMENT '강의',
                                              `question_count` tinyint NOT NULL COMMENT '문항 수',
                                              `correct_count` tinyint NOT NULL COMMENT '맞힌 수',
                                              `score` tinyint NOT NULL COMMENT '점수 0~100',
                                              `is_passed` tinyint(1) NOT NULL COMMENT '통과 여부 (70점 이상, 초안)',
    `submitted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '제출 일시',
    PRIMARY KEY (`quiz_attempt_id`),
    KEY `fk_quiz_attempt_member_id` (`member_id`),
    KEY `fk_quiz_attempt_course_id` (`course_id`),
    CONSTRAINT `fk_quiz_attempt_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`course_id`),
    CONSTRAINT `fk_quiz_attempt_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `ck_quiz_attempt_1` CHECK ((`score` between 0 and 100))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='퀴즈 응시';

SET FOREIGN_KEY_CHECKS = 1;
