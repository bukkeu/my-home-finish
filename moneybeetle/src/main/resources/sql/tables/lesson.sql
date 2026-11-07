-- lesson 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS lesson;

-- lesson 테이블 생성 (강의 영상·요약글)
CREATE TABLE IF NOT EXISTS `lesson` (
                                        `lesson_id` bigint NOT NULL AUTO_INCREMENT COMMENT '영상 ID',
                                        `course_id` bigint NOT NULL COMMENT '강의',
                                        `sort_order` tinyint NOT NULL COMMENT '영상 순서 1~3',
                                        `title` varchar(100) NOT NULL COMMENT '영상 제목',
    `video_url` varchar(500) NOT NULL COMMENT '영상 주소 (업로드/외부 링크 미정)',
    `duration_seconds` int NOT NULL DEFAULT '0' COMMENT '재생 시간(초)',
    `summary` text COMMENT '영상 요약글 (2,000자 이내)',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`lesson_id`),
    UNIQUE KEY `uk_lesson_1` (`course_id`,`sort_order`),
    CONSTRAINT `fk_lesson_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`course_id`),
    CONSTRAINT `ck_lesson_1` CHECK ((`sort_order` between 1 and 3))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='강의 영상·요약글';

SET FOREIGN_KEY_CHECKS = 1;
