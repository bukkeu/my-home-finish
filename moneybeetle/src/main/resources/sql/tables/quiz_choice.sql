-- quiz_choice 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS quiz_choice;

-- quiz_choice 테이블 생성 (퀴즈 보기)
CREATE TABLE IF NOT EXISTS `quiz_choice` (
                                             `quiz_choice_id` bigint NOT NULL AUTO_INCREMENT COMMENT '보기 ID',
                                             `quiz_question_id` bigint NOT NULL COMMENT '문항',
                                             `content` varchar(200) NOT NULL COMMENT '보기 내용',
    `is_correct` tinyint(1) NOT NULL DEFAULT '0' COMMENT '정답 여부 (제출 전에는 화면으로 보내지 않음)',
    `sort_order` tinyint NOT NULL COMMENT '보기 순서',
    PRIMARY KEY (`quiz_choice_id`),
    KEY `fk_quiz_choice_quiz_question_id` (`quiz_question_id`),
    CONSTRAINT `fk_quiz_choice_quiz_question_id` FOREIGN KEY (`quiz_question_id`) REFERENCES `quiz_question` (`quiz_question_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='퀴즈 보기';

SET FOREIGN_KEY_CHECKS = 1;
