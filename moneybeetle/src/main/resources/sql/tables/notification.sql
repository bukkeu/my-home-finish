-- notification 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS notification;

-- notification 테이블 생성 (알림)
CREATE TABLE IF NOT EXISTS `notification` (
                                              `notification_id` bigint NOT NULL AUTO_INCREMENT COMMENT '알림 ID',
                                              `member_id` bigint NOT NULL COMMENT '받는 회원',
                                              `type` varchar(30) NOT NULL COMMENT 'UNLOCK / INQUIRY_REPLY / HOUSING_RESULT / RANKING 등',
    `title` varchar(100) NOT NULL COMMENT '제목',
    `content` varchar(500) DEFAULT NULL COMMENT '내용',
    `link_url` varchar(500) DEFAULT NULL COMMENT '눌렀을 때 이동할 화면',
    `is_read` tinyint(1) NOT NULL DEFAULT '0' COMMENT '읽음 여부',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`notification_id`),
    KEY `idx_notification_1` (`member_id`,`is_read`,`created_at`),
    CONSTRAINT `fk_notification_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='알림';

SET FOREIGN_KEY_CHECKS = 1;
