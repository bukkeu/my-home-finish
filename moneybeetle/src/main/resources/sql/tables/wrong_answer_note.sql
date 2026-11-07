-- wrong_answer_note 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS wrong_answer_note;

-- wrong_answer_note 테이블 생성 (오답노트)
CREATE TABLE IF NOT EXISTS `wrong_answer_note` (
                                                   `wrong_answer_note_id` bigint NOT NULL AUTO_INCREMENT COMMENT '오답 ID',
                                                   `member_id` bigint NOT NULL COMMENT '회원',
                                                   `quiz_question_id` bigint NOT NULL COMMENT '틀린 문항',
                                                   `last_choice_id` bigint DEFAULT NULL COMMENT '마지막으로 고른 오답',
                                                   `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
                                                   PRIMARY KEY (`wrong_answer_note_id`),
    UNIQUE KEY `uk_wrong_answer_note_1` (`member_id`,`quiz_question_id`),
    KEY `fk_wrong_answer_note_quiz_question_id` (`quiz_question_id`),
    KEY `fk_wrong_answer_note_last_choice_id` (`last_choice_id`),
    CONSTRAINT `fk_wrong_answer_note_last_choice_id` FOREIGN KEY (`last_choice_id`) REFERENCES `quiz_choice` (`quiz_choice_id`),
    CONSTRAINT `fk_wrong_answer_note_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_wrong_answer_note_quiz_question_id` FOREIGN KEY (`quiz_question_id`) REFERENCES `quiz_question` (`quiz_question_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='오답노트';

SET FOREIGN_KEY_CHECKS = 1;
