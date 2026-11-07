-- learning_progress 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS learning_progress;

-- learning_progress 테이블 생성 (학습 진행)
CREATE TABLE IF NOT EXISTS `learning_progress` (
                                                   `learning_progress_id` bigint NOT NULL AUTO_INCREMENT COMMENT '진행 ID',
                                                   `member_id` bigint NOT NULL COMMENT '회원',
                                                   `course_id` bigint NOT NULL COMMENT '강의',
                                                   `status` varchar(20) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS / COMPLETED',
    `last_lesson_id` bigint DEFAULT NULL COMMENT '마지막으로 본 영상',
    `last_position_seconds` int NOT NULL DEFAULT '0' COMMENT '마지막 재생 위치(초)',
    `completed_at` datetime DEFAULT NULL COMMENT '학습 완료 일시 (퀴즈 최초 통과)',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`learning_progress_id`),
    UNIQUE KEY `uk_learning_progress_1` (`member_id`,`course_id`),
    KEY `idx_learning_progress_1` (`member_id`,`status`),
    KEY `fk_learning_progress_course_id` (`course_id`),
    KEY `fk_learning_progress_last_lesson_id` (`last_lesson_id`),
    CONSTRAINT `fk_learning_progress_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`course_id`),
    CONSTRAINT `fk_learning_progress_last_lesson_id` FOREIGN KEY (`last_lesson_id`) REFERENCES `lesson` (`lesson_id`),
    CONSTRAINT `fk_learning_progress_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='학습 진행';

SET FOREIGN_KEY_CHECKS = 1;
