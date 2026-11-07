-- quiz_answer 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS quiz_answer;

-- quiz_answer 테이블 생성 (퀴즈 답안)
CREATE TABLE IF NOT EXISTS `quiz_answer` (
                                             `quiz_answer_id` bigint NOT NULL AUTO_INCREMENT COMMENT '답안 ID',
                                             `quiz_attempt_id` bigint NOT NULL COMMENT '응시',
                                             `quiz_question_id` bigint NOT NULL COMMENT '문항',
                                             `selected_choice_id` bigint DEFAULT NULL COMMENT '고른 보기. 안 풀었으면 NULL',
                                             `is_correct` tinyint(1) NOT NULL COMMENT '정답 여부',
    PRIMARY KEY (`quiz_answer_id`),
    UNIQUE KEY `uk_quiz_answer_1` (`quiz_attempt_id`,`quiz_question_id`),
    KEY `fk_quiz_answer_quiz_question_id` (`quiz_question_id`),
    KEY `fk_quiz_answer_selected_choice_id` (`selected_choice_id`),
    CONSTRAINT `fk_quiz_answer_quiz_attempt_id` FOREIGN KEY (`quiz_attempt_id`) REFERENCES `quiz_attempt` (`quiz_attempt_id`),
    CONSTRAINT `fk_quiz_answer_quiz_question_id` FOREIGN KEY (`quiz_question_id`) REFERENCES `quiz_question` (`quiz_question_id`),
    CONSTRAINT `fk_quiz_answer_selected_choice_id` FOREIGN KEY (`selected_choice_id`) REFERENCES `quiz_choice` (`quiz_choice_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='퀴즈 답안';

SET FOREIGN_KEY_CHECKS = 1;
