-- lesson_watch 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS lesson_watch;

-- lesson_watch 테이블 생성 (영상 시청 기록)
CREATE TABLE IF NOT EXISTS `lesson_watch` (
                                              `lesson_watch_id` bigint NOT NULL AUTO_INCREMENT COMMENT '시청 ID',
                                              `member_id` bigint NOT NULL COMMENT '회원',
                                              `lesson_id` bigint NOT NULL COMMENT '영상',
                                              `watched_seconds` int NOT NULL DEFAULT '0' COMMENT '본 시간(초)',
                                              `is_completed` tinyint(1) NOT NULL DEFAULT '0' COMMENT '끝까지 시청 여부',
    `last_watched_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '마지막 시청 일시',
    PRIMARY KEY (`lesson_watch_id`),
    UNIQUE KEY `uk_lesson_watch_1` (`member_id`,`lesson_id`),
    KEY `idx_lesson_watch_1` (`member_id`,`last_watched_at`),
    KEY `fk_lesson_watch_lesson_id` (`lesson_id`),
    CONSTRAINT `fk_lesson_watch_lesson_id` FOREIGN KEY (`lesson_id`) REFERENCES `lesson` (`lesson_id`),
    CONSTRAINT `fk_lesson_watch_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='영상 시청 기록';

SET FOREIGN_KEY_CHECKS = 1;
