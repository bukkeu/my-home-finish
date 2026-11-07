USE money_beetle;

-- 외래키 순서와 상관없이 삭제·생성할 수 있도록 검사를 잠시 끈다
SET FOREIGN_KEY_CHECKS = 0;

-- 재실행 시 기존 테이블 삭제 (데이터도 함께 삭제된다)
DROP TABLE IF EXISTS admin_log;
DROP TABLE IF EXISTS attendance;
DROP TABLE IF EXISTS cash_history;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS course;
DROP TABLE IF EXISTS course_review;
DROP TABLE IF EXISTS economic_term;
DROP TABLE IF EXISTS exp_history;
DROP TABLE IF EXISTS forbidden_word;
DROP TABLE IF EXISTS holding;
DROP TABLE IF EXISTS housing_application;
DROP TABLE IF EXISTS housing_complex;
DROP TABLE IF EXISTS inquiry;
DROP TABLE IF EXISTS inquiry_reply;
DROP TABLE IF EXISTS instructor_profile;
DROP TABLE IF EXISTS learning_progress;
DROP TABLE IF EXISTS lesson;
DROP TABLE IF EXISTS lesson_watch;
DROP TABLE IF EXISTS level_policy;
DROP TABLE IF EXISTS member;
DROP TABLE IF EXISTS member_terms_agreement;
DROP TABLE IF EXISTS news;
DROP TABLE IF EXISTS news_tag;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS password_reset_token;
DROP TABLE IF EXISTS quiz_answer;
DROP TABLE IF EXISTS quiz_attempt;
DROP TABLE IF EXISTS quiz_choice;
DROP TABLE IF EXISTS quiz_question;
DROP TABLE IF EXISTS ranking_award;
DROP TABLE IF EXISTS reward_rule;
DROP TABLE IF EXISTS scrap;
DROP TABLE IF EXISTS season_participant;
DROP TABLE IF EXISTS season_turn;
DROP TABLE IF EXISTS sector;
DROP TABLE IF EXISTS social_account;
DROP TABLE IF EXISTS stock;
DROP TABLE IF EXISTS stock_price;
DROP TABLE IF EXISTS subscription_account;
DROP TABLE IF EXISTS subscription_deposit;
DROP TABLE IF EXISTS system_setting;
DROP TABLE IF EXISTS term_schedule;
DROP TABLE IF EXISTS terms;
DROP TABLE IF EXISTS trade_order;
DROP TABLE IF EXISTS trade_reason;
DROP TABLE IF EXISTS trading_season;
DROP TABLE IF EXISTS wrong_answer_note;

-- admin_log 테이블 생성 (관리자 작업 기록)
CREATE TABLE IF NOT EXISTS `admin_log` (
                                           `admin_log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '기록 ID',
                                           `admin_id` bigint NOT NULL COMMENT '작업한 관리자',
                                           `action` varchar(50) NOT NULL COMMENT 'TURN_ADVANCE / SETTLE / RANKING_PUBLISH / CASH_GIVE 등',
    `target_type` varchar(30) NOT NULL COMMENT '대상 종류 (SEASON, MEMBER, COURSE …)',
    `target_id` bigint DEFAULT NULL COMMENT '대상 ID',
    `detail` json DEFAULT NULL COMMENT '변경 전·후 값',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`admin_log_id`),
    KEY `idx_admin_log_1` (`target_type`,`target_id`),
    KEY `idx_admin_log_2` (`admin_id`,`created_at`),
    CONSTRAINT `fk_admin_log_admin_id` FOREIGN KEY (`admin_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자 작업 기록';

-- attendance 테이블 생성 (출석)
CREATE TABLE IF NOT EXISTS `attendance` (
                                            `attendance_id` bigint NOT NULL AUTO_INCREMENT COMMENT '출석 ID',
                                            `member_id` bigint NOT NULL COMMENT '회원',
                                            `attend_date` date NOT NULL COMMENT '출석일 (KST)',
                                            `streak_count` int NOT NULL COMMENT '이날 기준 연속 출석 일수',
                                            `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
                                            PRIMARY KEY (`attendance_id`),
    UNIQUE KEY `uk_attendance_1` (`member_id`,`attend_date`),
    CONSTRAINT `fk_attendance_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='출석';

-- cash_history 테이블 생성 (캐시 내역)
CREATE TABLE IF NOT EXISTS `cash_history` (
                                              `cash_history_id` bigint NOT NULL AUTO_INCREMENT COMMENT '내역 ID',
                                              `member_id` bigint NOT NULL COMMENT '회원',
                                              `history_type` varchar(20) NOT NULL COMMENT 'EARN / USE / ADMIN_GIVE / ADMIN_TAKE',
    `source_type` varchar(30) NOT NULL COMMENT 'ATTENDANCE / COURSE_COMPLETE / LEVEL_UP / SEASON_JOIN / SUBSCRIPTION_DEPOSIT / ADMIN',
    `source_id` bigint DEFAULT NULL COMMENT '원인 데이터 ID (출석 ID, 강의 ID, 레벨 등)',
    `reward_rule_id` bigint DEFAULT NULL COMMENT '적용한 보상 규칙',
    `amount` bigint NOT NULL COMMENT '변동 금액 (사용·회수는 음수)',
    `balance_after` bigint NOT NULL COMMENT '변동 후 잔액',
    `reason` varchar(200) DEFAULT NULL COMMENT '관리자 지급·회수 사유 (필수)',
    `admin_id` bigint DEFAULT NULL COMMENT '처리한 관리자',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`cash_history_id`),
    UNIQUE KEY `uk_cash_history_1` (`member_id`,`source_type`,`source_id`),
    KEY `idx_cash_history_1` (`member_id`,`created_at`),
    KEY `fk_cash_history_reward_rule_id` (`reward_rule_id`),
    KEY `fk_cash_history_admin_id` (`admin_id`),
    CONSTRAINT `fk_cash_history_admin_id` FOREIGN KEY (`admin_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_cash_history_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_cash_history_reward_rule_id` FOREIGN KEY (`reward_rule_id`) REFERENCES `reward_rule` (`reward_rule_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='캐시 내역';

-- category 테이블 생성 (카테고리·소주제)
CREATE TABLE IF NOT EXISTS `category` (
                                          `category_id` bigint NOT NULL AUTO_INCREMENT COMMENT '카테고리 ID',
                                          `parent_id` bigint DEFAULT NULL COMMENT '상위 카테고리. NULL이면 최상위',
                                          `name` varchar(30) NOT NULL COMMENT '이름',
    `depth` tinyint NOT NULL DEFAULT '1' COMMENT '1 카테고리 / 2 소주제',
    `sort_order` int NOT NULL DEFAULT '0' COMMENT '정렬 순서',
    `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '사용 여부',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`category_id`),
    KEY `fk_category_parent_id` (`parent_id`),
    CONSTRAINT `fk_category_parent_id` FOREIGN KEY (`parent_id`) REFERENCES `category` (`category_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='카테고리·소주제';

-- course 테이블 생성 (강의)
CREATE TABLE IF NOT EXISTS `course` (
                                        `course_id` bigint NOT NULL AUTO_INCREMENT COMMENT '강의 ID',
                                        `instructor_id` bigint NOT NULL COMMENT '강사',
                                        `category_id` bigint NOT NULL COMMENT '소주제',
                                        `title` varchar(100) NOT NULL COMMENT '강의 제목',
    `description` text COMMENT '강의 소개',
    `thumbnail_url` varchar(500) DEFAULT NULL COMMENT '썸네일',
    `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT 작성 중 / REVIEW 검수 중 / APPROVED 허가 / REJECTED 반려',
    `is_public` tinyint(1) NOT NULL DEFAULT '0' COMMENT '공개 여부 (허가 후에도 비공개 가능)',
    `info_base_date` date DEFAULT NULL COMMENT '제도 정보 기준일 (NFR-15)',
    `info_source` varchar(200) DEFAULT NULL COMMENT '제도 정보 출처',
    `submitted_at` datetime DEFAULT NULL COMMENT '검수 요청 일시',
    `approved_at` datetime DEFAULT NULL COMMENT '허가 일시',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    `deleted_at` datetime DEFAULT NULL COMMENT '삭제 일시 (Soft Delete)',
    PRIMARY KEY (`course_id`),
    KEY `idx_course_1` (`category_id`,`status`,`is_public`),
    KEY `fk_course_instructor_id` (`instructor_id`),
    CONSTRAINT `fk_course_category_id` FOREIGN KEY (`category_id`) REFERENCES `category` (`category_id`),
    CONSTRAINT `fk_course_instructor_id` FOREIGN KEY (`instructor_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='강의';

-- course_review 테이블 생성 (강의 검수 이력)
CREATE TABLE IF NOT EXISTS `course_review` (
                                               `course_review_id` bigint NOT NULL AUTO_INCREMENT COMMENT '검수 ID',
                                               `course_id` bigint NOT NULL COMMENT '강의',
                                               `admin_id` bigint NOT NULL COMMENT '검수한 관리자',
                                               `result` varchar(20) NOT NULL COMMENT 'APPROVED / REJECTED',
    `reason` varchar(500) DEFAULT NULL COMMENT '반려 사유 (반려 시 필수)',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`course_review_id`),
    KEY `fk_course_review_course_id` (`course_id`),
    KEY `fk_course_review_admin_id` (`admin_id`),
    CONSTRAINT `fk_course_review_admin_id` FOREIGN KEY (`admin_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_course_review_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`course_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='강의 검수 이력';

-- economic_term 테이블 생성 (경제 용어)
CREATE TABLE IF NOT EXISTS `economic_term` (
                                               `economic_term_id` bigint NOT NULL AUTO_INCREMENT COMMENT '용어 ID',
                                               `term` varchar(50) NOT NULL COMMENT '용어',
    `description` varchar(500) NOT NULL COMMENT '뜻',
    `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '사용 여부',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`economic_term_id`),
    UNIQUE KEY `uk_economic_term_term` (`term`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='경제 용어';

-- exp_history 테이블 생성 (경험치 내역)
CREATE TABLE IF NOT EXISTS `exp_history` (
                                             `exp_history_id` bigint NOT NULL AUTO_INCREMENT COMMENT '내역 ID',
                                             `member_id` bigint NOT NULL COMMENT '회원',
                                             `source_type` varchar(30) NOT NULL COMMENT 'COURSE_COMPLETE / QUIZ_PERFECT',
    `source_id` bigint NOT NULL COMMENT '원인 강의 ID',
    `reward_rule_id` bigint DEFAULT NULL COMMENT '적용한 보상 규칙',
    `amount` int NOT NULL COMMENT '지급 경험치',
    `exp_after` int NOT NULL COMMENT '지급 후 누적 경험치',
    `level_after` tinyint NOT NULL COMMENT '지급 후 레벨',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`exp_history_id`),
    UNIQUE KEY `uk_exp_history_1` (`member_id`,`source_type`,`source_id`),
    KEY `idx_exp_history_1` (`member_id`,`created_at`),
    KEY `fk_exp_history_reward_rule_id` (`reward_rule_id`),
    CONSTRAINT `fk_exp_history_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_exp_history_reward_rule_id` FOREIGN KEY (`reward_rule_id`) REFERENCES `reward_rule` (`reward_rule_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='경험치 내역';

-- forbidden_word 테이블 생성 (금칙어)
CREATE TABLE IF NOT EXISTS `forbidden_word` (
                                                `forbidden_word_id` bigint NOT NULL AUTO_INCREMENT COMMENT '금칙어 ID',
                                                `word` varchar(50) NOT NULL COMMENT '단어',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`forbidden_word_id`),
    UNIQUE KEY `uk_forbidden_word_word` (`word`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='금칙어';

-- holding 테이블 생성 (보유 종목)
CREATE TABLE IF NOT EXISTS `holding` (
                                         `holding_id` bigint NOT NULL AUTO_INCREMENT COMMENT '보유 ID',
                                         `season_participant_id` bigint NOT NULL COMMENT '참가자',
                                         `stock_id` bigint NOT NULL COMMENT '종목',
                                         `quantity` int NOT NULL DEFAULT '0' COMMENT '보유 수량 (0 이상)',
                                         `avg_price` int NOT NULL DEFAULT '0' COMMENT '평균 매수 단가',
                                         `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
                                         PRIMARY KEY (`holding_id`),
    UNIQUE KEY `uk_holding_1` (`season_participant_id`,`stock_id`),
    KEY `fk_holding_stock_id` (`stock_id`),
    CONSTRAINT `fk_holding_season_participant_id` FOREIGN KEY (`season_participant_id`) REFERENCES `season_participant` (`season_participant_id`),
    CONSTRAINT `fk_holding_stock_id` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`stock_id`),
    CONSTRAINT `ck_holding_1` CHECK ((`quantity` >= 0))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='보유 종목';

-- housing_application 테이블 생성 (청약 신청)
CREATE TABLE IF NOT EXISTS `housing_application` (
                                                     `housing_application_id` bigint NOT NULL AUTO_INCREMENT COMMENT '신청 ID',
                                                     `member_id` bigint NOT NULL COMMENT '신청자',
                                                     `housing_complex_id` bigint NOT NULL COMMENT '단지',
                                                     `receipt_no` varchar(20) NOT NULL COMMENT '접수번호',
    `homeless_years` tinyint NOT NULL COMMENT '입력한 무주택 기간(년)',
    `dependents` tinyint NOT NULL COMMENT '입력한 부양가족 수',
    `paid_count_snapshot` int NOT NULL COMMENT '신청 시점 납입회차',
    `homeless_score` tinyint NOT NULL COMMENT '무주택 점수 (만점 32)',
    `dependent_score` tinyint NOT NULL COMMENT '부양가족 점수 (만점 35)',
    `account_score` tinyint NOT NULL COMMENT '통장 점수 (만점 17)',
    `total_score` tinyint NOT NULL COMMENT '총 가점 (만점 84)',
    `result` varchar(10) DEFAULT NULL COMMENT 'WIN 당첨 / LOSE 낙첨. 발표 전 NULL',
    `result_rank` int DEFAULT NULL COMMENT '산정 순위',
    `applied_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청 일시',
    PRIMARY KEY (`housing_application_id`),
    UNIQUE KEY `uk_housing_application_receipt_no` (`receipt_no`),
    UNIQUE KEY `uk_housing_application_1` (`member_id`,`housing_complex_id`),
    KEY `idx_housing_application_1` (`housing_complex_id`,`total_score`),
    CONSTRAINT `fk_housing_application_housing_complex_id` FOREIGN KEY (`housing_complex_id`) REFERENCES `housing_complex` (`housing_complex_id`),
    CONSTRAINT `fk_housing_application_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='청약 신청';

-- housing_complex 테이블 생성 (청약 단지·공고)
CREATE TABLE IF NOT EXISTS `housing_complex` (
                                                 `housing_complex_id` bigint NOT NULL AUTO_INCREMENT COMMENT '단지 ID',
                                                 `name` varchar(100) NOT NULL COMMENT '단지 이름',
    `location` varchar(100) NOT NULL COMMENT '위치 (가상)',
    `area_type` varchar(30) NOT NULL COMMENT '평형 (예: 84㎡)',
    `sale_price` bigint NOT NULL COMMENT '분양가',
    `supply_units` int NOT NULL COMMENT '공급 세대 수',
    `supply_type` varchar(30) NOT NULL COMMENT '공급 유형 (일반·특별공급 등)',
    `notice_date` date NOT NULL COMMENT '공고일',
    `apply_start_date` date NOT NULL COMMENT '신청 시작일',
    `apply_end_date` date NOT NULL COMMENT '신청 마감일',
    `announce_date` date NOT NULL COMMENT '발표일',
    `status` varchar(20) NOT NULL DEFAULT 'UPCOMING' COMMENT 'UPCOMING / OPEN / CLOSED / ANNOUNCED',
    `computed_at` datetime DEFAULT NULL COMMENT '당첨 산정 일시',
    `announced_at` datetime DEFAULT NULL COMMENT '결과 발표 일시',
    `created_by` bigint NOT NULL COMMENT '등록한 관리자',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    `deleted_at` datetime DEFAULT NULL COMMENT '삭제 일시 (Soft Delete)',
    PRIMARY KEY (`housing_complex_id`),
    KEY `idx_housing_complex_1` (`apply_start_date`,`apply_end_date`),
    KEY `fk_housing_complex_created_by` (`created_by`),
    CONSTRAINT `fk_housing_complex_created_by` FOREIGN KEY (`created_by`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='청약 단지·공고';

-- inquiry 테이블 생성 (문의글)
CREATE TABLE IF NOT EXISTS `inquiry` (
                                         `inquiry_id` bigint NOT NULL AUTO_INCREMENT COMMENT '문의 ID',
                                         `member_id` bigint NOT NULL COMMENT '작성자',
                                         `course_id` bigint DEFAULT NULL COMMENT '문의한 강의. NULL이면 사이트 문의',
                                         `category` varchar(30) DEFAULT NULL COMMENT '사이트 문의 분류 (계정·캐시, 모의 청약 등)',
    `title` varchar(50) NOT NULL COMMENT '제목 50자',
    `content` varchar(1000) NOT NULL COMMENT '내용 1,000자',
    `status` varchar(20) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING 답변 대기 / ANSWERED 답변 완료',
    `is_hidden` tinyint(1) NOT NULL DEFAULT '0' COMMENT '관리자 숨김 여부',
    `hidden_by` bigint DEFAULT NULL COMMENT '숨김 처리한 관리자',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    `deleted_at` datetime DEFAULT NULL COMMENT '삭제 일시 (Soft Delete)',
    PRIMARY KEY (`inquiry_id`),
    KEY `idx_inquiry_1` (`course_id`,`created_at`),
    KEY `idx_inquiry_2` (`member_id`,`created_at`),
    KEY `fk_inquiry_hidden_by` (`hidden_by`),
    CONSTRAINT `fk_inquiry_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`course_id`),
    CONSTRAINT `fk_inquiry_hidden_by` FOREIGN KEY (`hidden_by`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_inquiry_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='문의글';

-- inquiry_reply 테이블 생성 (문의 답글)
CREATE TABLE IF NOT EXISTS `inquiry_reply` (
                                               `inquiry_reply_id` bigint NOT NULL AUTO_INCREMENT COMMENT '답글 ID',
                                               `inquiry_id` bigint NOT NULL COMMENT '문의',
                                               `writer_id` bigint NOT NULL COMMENT '작성자 (강사·관리자·질문자)',
                                               `content` varchar(1000) NOT NULL COMMENT '내용',
    `is_hidden` tinyint(1) NOT NULL DEFAULT '0' COMMENT '관리자 숨김 여부',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    `deleted_at` datetime DEFAULT NULL COMMENT '삭제 일시 (Soft Delete)',
    PRIMARY KEY (`inquiry_reply_id`),
    KEY `fk_inquiry_reply_inquiry_id` (`inquiry_id`),
    KEY `fk_inquiry_reply_writer_id` (`writer_id`),
    CONSTRAINT `fk_inquiry_reply_inquiry_id` FOREIGN KEY (`inquiry_id`) REFERENCES `inquiry` (`inquiry_id`),
    CONSTRAINT `fk_inquiry_reply_writer_id` FOREIGN KEY (`writer_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='문의 답글';

-- instructor_profile 테이블 생성 (강사 정보)
CREATE TABLE IF NOT EXISTS `instructor_profile` (
                                                    `instructor_profile_id` bigint NOT NULL AUTO_INCREMENT COMMENT '강사 정보 ID',
                                                    `member_id` bigint NOT NULL COMMENT '강사 회원 (1:1)',
                                                    `display_name` varchar(30) NOT NULL COMMENT '강의에 표시할 이름',
    `field` varchar(100) DEFAULT NULL COMMENT '전문 분야 (예: 청약, ETF)',
    `approval_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REVOKED',
    `approved_by` bigint DEFAULT NULL COMMENT '승인한 관리자',
    `approved_at` datetime DEFAULT NULL COMMENT '승인 일시',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`instructor_profile_id`),
    UNIQUE KEY `uk_instructor_profile_member_id` (`member_id`),
    KEY `fk_instructor_profile_approved_by` (`approved_by`),
    CONSTRAINT `fk_instructor_profile_approved_by` FOREIGN KEY (`approved_by`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_instructor_profile_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='강사 정보';

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

-- level_policy 테이블 생성 (레벨 정책)
CREATE TABLE IF NOT EXISTS `level_policy` (
                                              `level_policy_id` bigint NOT NULL AUTO_INCREMENT COMMENT '레벨 정책 ID',
                                              `level` tinyint NOT NULL COMMENT '레벨 1~20',
                                              `required_exp` int NOT NULL COMMENT '이 레벨이 되기 위한 누적 경험치 (150 XP 간격, 초안)',
                                              `title` varchar(20) NOT NULL COMMENT '칭호: 텐트 / 원룸 / 투룸 / 아파트 / 내 집',
    `levelup_cash` int NOT NULL DEFAULT '0' COMMENT '레벨업 캐시 (레벨 × 1,000, 초안)',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`level_policy_id`),
    UNIQUE KEY `uk_level_policy_level` (`level`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='레벨 정책';

-- member 테이블 생성 (회원)
CREATE TABLE IF NOT EXISTS `member` (
                                        `member_id` bigint NOT NULL AUTO_INCREMENT COMMENT '회원 ID',
                                        `email` varchar(100) DEFAULT NULL COMMENT '로그인 아이디. 소문자로 저장. 탈퇴 30일 후 NULL',
    `password_hash` varchar(255) DEFAULT NULL COMMENT '비밀번호 해시. 간편 로그인만 쓰면 NULL',
    `nickname` varchar(30) NOT NULL COMMENT '닉네임 2~12자, 한글·영문·숫자 (탈퇴 시 "탈퇴한 사용자#ID"가 들어가도록 여유 길이)',
    `profile_image_url` varchar(500) DEFAULT NULL COMMENT '프로필 이미지 경로 (2MB, jpg·png)',
    `birth_date` date DEFAULT NULL COMMENT '생년월일. 만 14세 이상 확인용. 탈퇴 30일 후 NULL',
    `gender` varchar(10) DEFAULT NULL COMMENT 'MALE / FEMALE (필수 여부 미정)',
    `is_identity_verified` tinyint(1) NOT NULL DEFAULT '0' COMMENT '본인인증 완료 여부 (결과값만 저장)',
    `role` varchar(20) NOT NULL DEFAULT 'MEMBER' COMMENT 'MEMBER / INSTRUCTOR / ADMIN',
    `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / SUSPENDED / WITHDRAWN',
    `suspended_reason` varchar(200) DEFAULT NULL COMMENT '정지 사유',
    `level` tinyint NOT NULL DEFAULT '1' COMMENT '레벨 1~20. 내려가지 않음',
    `exp` int NOT NULL DEFAULT '0' COMMENT '누적 경험치',
    `cash` bigint NOT NULL DEFAULT '0' COMMENT '보유 캐시. 0 미만 불가',
    `attendance_streak` int NOT NULL DEFAULT '0' COMMENT '현재 연속 출석 일수',
    `last_attended_date` date DEFAULT NULL COMMENT '마지막 출석일 (KST)',
    `failed_login_count` tinyint NOT NULL DEFAULT '0' COMMENT '연속 로그인 실패 횟수',
    `locked_until` datetime DEFAULT NULL COMMENT '로그인 잠금 해제 시각 (5회 실패 시 +10분)',
    `last_login_at` datetime DEFAULT NULL COMMENT '마지막 로그인',
    `withdrawn_at` datetime DEFAULT NULL COMMENT '탈퇴 일시',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`member_id`),
    UNIQUE KEY `uk_member_nickname` (`nickname`),
    UNIQUE KEY `uk_member_email` (`email`),
    CONSTRAINT `ck_member_1` CHECK ((`cash` >= 0)),
    CONSTRAINT `ck_member_2` CHECK ((`level` between 1 and 20)),
    CONSTRAINT `ck_member_3` CHECK ((`exp` >= 0))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원';

-- member_terms_agreement 테이블 생성 (약관 동의)
CREATE TABLE IF NOT EXISTS `member_terms_agreement` (
                                                        `member_terms_agreement_id` bigint NOT NULL AUTO_INCREMENT COMMENT '동의 ID',
                                                        `member_id` bigint NOT NULL COMMENT '회원',
                                                        `terms_id` bigint NOT NULL COMMENT '약관',
                                                        `is_agreed` tinyint(1) NOT NULL COMMENT '동의 여부',
    `agreed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '동의 일시',
    PRIMARY KEY (`member_terms_agreement_id`),
    UNIQUE KEY `uk_member_terms_agreement_1` (`member_id`,`terms_id`),
    KEY `fk_member_terms_agreement_terms_id` (`terms_id`),
    CONSTRAINT `fk_member_terms_agreement_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_member_terms_agreement_terms_id` FOREIGN KEY (`terms_id`) REFERENCES `terms` (`terms_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='약관 동의';

-- news 테이블 생성 (가상 뉴스)
CREATE TABLE IF NOT EXISTS `news` (
                                      `news_id` bigint NOT NULL AUTO_INCREMENT COMMENT '뉴스 ID',
                                      `trading_season_id` bigint NOT NULL COMMENT '시즌',
                                      `turn_no` tinyint NOT NULL COMMENT '공개 턴',
                                      `title` varchar(100) NOT NULL COMMENT '제목',
    `content` text NOT NULL COMMENT '본문',
    `admin_memo` varchar(500) DEFAULT NULL COMMENT '설계 의도 메모 (관리자 전용)',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`news_id`),
    KEY `idx_news_1` (`trading_season_id`,`turn_no`),
    CONSTRAINT `fk_news_trading_season_id` FOREIGN KEY (`trading_season_id`) REFERENCES `trading_season` (`trading_season_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='가상 뉴스';

-- news_tag 테이블 생성 (뉴스 관련 분야·종목)
CREATE TABLE IF NOT EXISTS `news_tag` (
                                          `news_tag_id` bigint NOT NULL AUTO_INCREMENT COMMENT '연결 ID',
                                          `news_id` bigint NOT NULL COMMENT '뉴스',
                                          `sector_id` bigint DEFAULT NULL COMMENT '관련 분야',
                                          `stock_id` bigint DEFAULT NULL COMMENT '관련 종목',
                                          PRIMARY KEY (`news_tag_id`),
    KEY `fk_news_tag_news_id` (`news_id`),
    KEY `fk_news_tag_sector_id` (`sector_id`),
    KEY `fk_news_tag_stock_id` (`stock_id`),
    CONSTRAINT `fk_news_tag_news_id` FOREIGN KEY (`news_id`) REFERENCES `news` (`news_id`),
    CONSTRAINT `fk_news_tag_sector_id` FOREIGN KEY (`sector_id`) REFERENCES `sector` (`sector_id`),
    CONSTRAINT `fk_news_tag_stock_id` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`stock_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='뉴스 관련 분야·종목';

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

-- password_reset_token 테이블 생성 (비밀번호 재설정 인증)
CREATE TABLE IF NOT EXISTS `password_reset_token` (
                                                      `password_reset_token_id` bigint NOT NULL AUTO_INCREMENT COMMENT '토큰 ID',
                                                      `member_id` bigint NOT NULL COMMENT '회원',
                                                      `token_hash` varchar(255) NOT NULL COMMENT '인증 토큰 해시',
    `expires_at` datetime NOT NULL COMMENT '만료 시각 (발급 + 10분)',
    `used_at` datetime DEFAULT NULL COMMENT '사용 시각. 사용하면 재사용 불가',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`password_reset_token_id`),
    UNIQUE KEY `uk_password_reset_token_token_hash` (`token_hash`),
    KEY `fk_password_reset_token_member_id` (`member_id`),
    CONSTRAINT `fk_password_reset_token_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='비밀번호 재설정 인증';

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

-- ranking_award 테이블 생성 (랭킹 수상)
CREATE TABLE IF NOT EXISTS `ranking_award` (
                                               `ranking_award_id` bigint NOT NULL AUTO_INCREMENT COMMENT '수상 ID',
                                               `trading_season_id` bigint NOT NULL COMMENT '시즌',
                                               `member_id` bigint NOT NULL COMMENT '회원',
                                               `ranking_type` varchar(10) NOT NULL COMMENT 'ASSET 순자산 / RETURN 수익률',
    `rank_no` tinyint NOT NULL COMMENT '순위 1~3',
    `badge_name` varchar(50) DEFAULT NULL COMMENT '뱃지·칭호 장식 이름',
    `awarded_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '지급 일시',
    PRIMARY KEY (`ranking_award_id`),
    UNIQUE KEY `uk_ranking_award_1` (`trading_season_id`,`member_id`,`ranking_type`),
    KEY `fk_ranking_award_member_id` (`member_id`),
    CONSTRAINT `fk_ranking_award_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_ranking_award_trading_season_id` FOREIGN KEY (`trading_season_id`) REFERENCES `trading_season` (`trading_season_id`),
    CONSTRAINT `ck_ranking_award_1` CHECK ((`rank_no` between 1 and 3))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='랭킹 수상';

-- reward_rule 테이블 생성 (보상 규칙)
CREATE TABLE IF NOT EXISTS `reward_rule` (
                                             `reward_rule_id` bigint NOT NULL AUTO_INCREMENT COMMENT '규칙 ID',
                                             `code` varchar(30) NOT NULL COMMENT 'COURSE_COMPLETE / QUIZ_PERFECT / ATTENDANCE_STREAK',
    `name` varchar(50) NOT NULL COMMENT '규칙 이름',
    `cash_amount` int NOT NULL DEFAULT '0' COMMENT '지급 캐시',
    `exp_amount` int NOT NULL DEFAULT '0' COMMENT '지급 경험치',
    `description` varchar(200) DEFAULT NULL COMMENT '지급 조건 설명',
    `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '사용 여부',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`reward_rule_id`),
    UNIQUE KEY `uk_reward_rule_code` (`code`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='보상 규칙';

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

-- season_participant 테이블 생성 (시즌 참가자)
CREATE TABLE IF NOT EXISTS `season_participant` (
                                                    `season_participant_id` bigint NOT NULL AUTO_INCREMENT COMMENT '참가 ID',
                                                    `trading_season_id` bigint NOT NULL COMMENT '시즌',
                                                    `member_id` bigint NOT NULL COMMENT '회원',
                                                    `saved_cash` bigint NOT NULL COMMENT '참가 때 가져온 캐시',
                                                    `carryover_in` bigint NOT NULL DEFAULT '0' COMMENT '지난 시즌 이월금',
                                                    `invest_amount` bigint NOT NULL COMMENT '투자 가능 금액 = 캐시 + 이월금 (변경 불가)',
                                                    `cash` bigint NOT NULL COMMENT '현재 보유 현금',
                                                    `final_net_asset` bigint DEFAULT NULL COMMENT '정산 순자산',
                                                    `return_rate` decimal(7,2) DEFAULT NULL COMMENT '정산 수익률(%)',
    `asset_rank` int DEFAULT NULL COMMENT '순자산 순위',
    `return_rank` int DEFAULT NULL COMMENT '수익률 순위',
    `carryover_out` bigint NOT NULL DEFAULT '0' COMMENT '다음 시즌 이월금',
    `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '참가 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`season_participant_id`),
    UNIQUE KEY `uk_season_participant_1` (`trading_season_id`,`member_id`),
    KEY `idx_season_participant_1` (`trading_season_id`,`final_net_asset`),
    KEY `idx_season_participant_2` (`trading_season_id`,`return_rate`),
    KEY `fk_season_participant_member_id` (`member_id`),
    CONSTRAINT `fk_season_participant_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_season_participant_trading_season_id` FOREIGN KEY (`trading_season_id`) REFERENCES `trading_season` (`trading_season_id`),
    CONSTRAINT `ck_season_participant_1` CHECK ((`cash` >= 0))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='시즌 참가자';

-- season_turn 테이블 생성 (시즌 턴)
CREATE TABLE IF NOT EXISTS `season_turn` (
                                             `season_turn_id` bigint NOT NULL AUTO_INCREMENT COMMENT '턴 ID',
                                             `trading_season_id` bigint NOT NULL COMMENT '시즌',
                                             `turn_no` tinyint NOT NULL COMMENT '턴 번호 1~12',
                                             `trade_date` date NOT NULL COMMENT '거래일 (일요일 제외)',
                                             `progressed_at` datetime DEFAULT NULL COMMENT '이 턴으로 넘긴 시각',
                                             `progressed_by` bigint DEFAULT NULL COMMENT '넘긴 관리자',
                                             PRIMARY KEY (`season_turn_id`),
    UNIQUE KEY `uk_season_turn_1` (`trading_season_id`,`turn_no`),
    KEY `fk_season_turn_progressed_by` (`progressed_by`),
    CONSTRAINT `fk_season_turn_progressed_by` FOREIGN KEY (`progressed_by`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_season_turn_trading_season_id` FOREIGN KEY (`trading_season_id`) REFERENCES `trading_season` (`trading_season_id`),
    CONSTRAINT `ck_season_turn_1` CHECK ((`turn_no` between 1 and 12))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='시즌 턴';

-- sector 테이블 생성 (분야)
CREATE TABLE IF NOT EXISTS `sector` (
                                        `sector_id` bigint NOT NULL AUTO_INCREMENT COMMENT '분야 ID',
                                        `name` varchar(30) NOT NULL COMMENT '분야 이름 (반도체, 바이오 등)',
    `sort_order` int NOT NULL DEFAULT '0' COMMENT '정렬 순서',
    `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '사용 여부',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`sector_id`),
    UNIQUE KEY `uk_sector_name` (`name`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='분야';

-- social_account 테이블 생성 (간편 로그인 연결)
CREATE TABLE IF NOT EXISTS `social_account` (
                                                `social_account_id` bigint NOT NULL AUTO_INCREMENT COMMENT '연결 ID',
                                                `member_id` bigint NOT NULL COMMENT '회원',
                                                `provider` varchar(20) NOT NULL COMMENT 'GOOGLE / KAKAO',
    `provider_user_id` varchar(100) NOT NULL COMMENT '제공자 쪽 사용자 ID',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`social_account_id`),
    UNIQUE KEY `uk_social_account_1` (`provider`,`provider_user_id`),
    KEY `fk_social_account_member_id` (`member_id`),
    CONSTRAINT `fk_social_account_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='간편 로그인 연결';

-- stock 테이블 생성 (가상 종목)
CREATE TABLE IF NOT EXISTS `stock` (
                                       `stock_id` bigint NOT NULL AUTO_INCREMENT COMMENT '종목 ID',
                                       `sector_id` bigint NOT NULL COMMENT '분야',
                                       `name` varchar(50) NOT NULL COMMENT '종목 이름',
    `start_price` int NOT NULL COMMENT '시작 가격',
    `intro` text COMMENT '회사 소개',
    `business` varchar(300) DEFAULT NULL COMMENT '주요 사업',
    `revenue` varchar(50) DEFAULT NULL COMMENT '가상 매출',
    `operating_profit` varchar(50) DEFAULT NULL COMMENT '가상 영업이익',
    `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '사용 여부',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`stock_id`),
    UNIQUE KEY `uk_stock_name` (`name`),
    KEY `fk_stock_sector_id` (`sector_id`),
    CONSTRAINT `fk_stock_sector_id` FOREIGN KEY (`sector_id`) REFERENCES `sector` (`sector_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='가상 종목';

-- stock_price 테이블 생성 (턴별 가격)
CREATE TABLE IF NOT EXISTS `stock_price` (
                                             `stock_price_id` bigint NOT NULL AUTO_INCREMENT COMMENT '가격 ID',
                                             `trading_season_id` bigint NOT NULL COMMENT '시즌',
                                             `stock_id` bigint NOT NULL COMMENT '종목',
                                             `turn_no` tinyint NOT NULL COMMENT '턴 1~12',
                                             `price` int NOT NULL COMMENT '이 턴의 고정 가격 (1 이상)',
                                             `change_rate` decimal(6,2) DEFAULT NULL COMMENT '지난 턴 대비 변동률(%)',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`stock_price_id`),
    UNIQUE KEY `uk_stock_price_1` (`trading_season_id`,`stock_id`,`turn_no`),
    KEY `fk_stock_price_stock_id` (`stock_id`),
    CONSTRAINT `fk_stock_price_stock_id` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`stock_id`),
    CONSTRAINT `fk_stock_price_trading_season_id` FOREIGN KEY (`trading_season_id`) REFERENCES `trading_season` (`trading_season_id`),
    CONSTRAINT `ck_stock_price_1` CHECK ((`price` > 0)),
    CONSTRAINT `ck_stock_price_2` CHECK ((`turn_no` between 1 and 12))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='턴별 가격';

-- subscription_account 테이블 생성 (청약통장)
CREATE TABLE IF NOT EXISTS `subscription_account` (
                                                      `subscription_account_id` bigint NOT NULL AUTO_INCREMENT COMMENT '통장 ID',
                                                      `member_id` bigint NOT NULL COMMENT '회원 (1:1)',
                                                      `balance` bigint NOT NULL DEFAULT '0' COMMENT '통장 잔액',
                                                      `paid_count` int NOT NULL DEFAULT '0' COMMENT '납입회차',
                                                      `last_paid_date` date DEFAULT NULL COMMENT '마지막 납입일 (KST)',
                                                      `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
                                                      `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
                                                      PRIMARY KEY (`subscription_account_id`),
    UNIQUE KEY `uk_subscription_account_member_id` (`member_id`),
    CONSTRAINT `fk_subscription_account_member_id` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='청약통장';

-- subscription_deposit 테이블 생성 (청약 납입 내역)
CREATE TABLE IF NOT EXISTS `subscription_deposit` (
                                                      `subscription_deposit_id` bigint NOT NULL AUTO_INCREMENT COMMENT '납입 ID',
                                                      `subscription_account_id` bigint NOT NULL COMMENT '통장',
                                                      `paid_date` date NOT NULL COMMENT '납입일 (KST)',
                                                      `amount` int NOT NULL COMMENT '납입 캐시 (1회 1,000, 초안)',
                                                      `paid_count_after` int NOT NULL COMMENT '납입 후 회차',
                                                      `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
                                                      PRIMARY KEY (`subscription_deposit_id`),
    UNIQUE KEY `uk_subscription_deposit_1` (`subscription_account_id`,`paid_date`),
    CONSTRAINT `fk_subscription_deposit_subscription_account_id` FOREIGN KEY (`subscription_account_id`) REFERENCES `subscription_account` (`subscription_account_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='청약 납입 내역';

-- system_setting 테이블 생성 (서비스 설정)
CREATE TABLE IF NOT EXISTS `system_setting` (
                                                `system_setting_id` bigint NOT NULL AUTO_INCREMENT COMMENT '설정 ID',
                                                `setting_key` varchar(50) NOT NULL COMMENT '키 (UNLOCK_LEVEL_TRADING, QUIZ_PASS_SCORE …)',
    `setting_value` varchar(200) NOT NULL COMMENT '값',
    `description` varchar(200) DEFAULT NULL COMMENT '설명',
    `updated_by` bigint DEFAULT NULL COMMENT '마지막으로 바꾼 관리자',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`system_setting_id`),
    UNIQUE KEY `uk_system_setting_setting_key` (`setting_key`),
    KEY `fk_system_setting_updated_by` (`updated_by`),
    CONSTRAINT `fk_system_setting_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='서비스 설정';

-- term_schedule 테이블 생성 (오늘의 경제 용어)
CREATE TABLE IF NOT EXISTS `term_schedule` (
                                               `term_schedule_id` bigint NOT NULL AUTO_INCREMENT COMMENT '일정 ID',
                                               `show_date` date NOT NULL COMMENT '노출일 (KST)',
                                               `economic_term_id` bigint NOT NULL COMMENT '용어',
                                               `sort_order` tinyint NOT NULL COMMENT '순서 1~5',
                                               `is_auto` tinyint(1) NOT NULL DEFAULT '0' COMMENT '자동 선정 여부 (관리자 미지정일)',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`term_schedule_id`),
    UNIQUE KEY `uk_term_schedule_1` (`show_date`,`sort_order`),
    UNIQUE KEY `uk_term_schedule_2` (`show_date`,`economic_term_id`),
    KEY `fk_term_schedule_economic_term_id` (`economic_term_id`),
    CONSTRAINT `fk_term_schedule_economic_term_id` FOREIGN KEY (`economic_term_id`) REFERENCES `economic_term` (`economic_term_id`),
    CONSTRAINT `ck_term_schedule_1` CHECK ((`sort_order` between 1 and 5))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='오늘의 경제 용어';

-- terms 테이블 생성 (약관)
CREATE TABLE IF NOT EXISTS `terms` (
                                       `terms_id` bigint NOT NULL AUTO_INCREMENT COMMENT '약관 ID',
                                       `title` varchar(100) NOT NULL COMMENT '약관 이름',
    `content` text NOT NULL COMMENT '약관 본문',
    `is_required` tinyint(1) NOT NULL COMMENT '필수 약관 여부',
    `version` varchar(20) NOT NULL COMMENT '버전 (예: 1.0)',
    `effective_at` datetime NOT NULL COMMENT '시행 일시',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`terms_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='약관';

-- trade_order 테이블 생성 (거래 내역)
CREATE TABLE IF NOT EXISTS `trade_order` (
                                             `trade_order_id` bigint NOT NULL AUTO_INCREMENT COMMENT '주문 ID',
                                             `season_participant_id` bigint NOT NULL COMMENT '참가자',
                                             `stock_id` bigint NOT NULL COMMENT '종목',
                                             `turn_no` tinyint NOT NULL COMMENT '체결 턴',
                                             `order_type` varchar(10) NOT NULL COMMENT 'BUY / SELL',
    `quantity` int NOT NULL COMMENT '수량 (1 이상 정수)',
    `price` int NOT NULL COMMENT '체결가 (그 턴의 고정 가격)',
    `amount` bigint NOT NULL COMMENT '체결 금액 = 수량 × 가격',
    `trade_reason_id` bigint NOT NULL COMMENT '매매 이유',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    PRIMARY KEY (`trade_order_id`),
    KEY `idx_trade_order_1` (`season_participant_id`,`turn_no`),
    KEY `fk_trade_order_stock_id` (`stock_id`),
    KEY `fk_trade_order_trade_reason_id` (`trade_reason_id`),
    CONSTRAINT `fk_trade_order_season_participant_id` FOREIGN KEY (`season_participant_id`) REFERENCES `season_participant` (`season_participant_id`),
    CONSTRAINT `fk_trade_order_stock_id` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`stock_id`),
    CONSTRAINT `fk_trade_order_trade_reason_id` FOREIGN KEY (`trade_reason_id`) REFERENCES `trade_reason` (`trade_reason_id`),
    CONSTRAINT `ck_trade_order_1` CHECK ((`quantity` > 0)),
    CONSTRAINT `ck_trade_order_2` CHECK ((`price` > 0))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='거래 내역';

-- trade_reason 테이블 생성 (매매 이유)
CREATE TABLE IF NOT EXISTS `trade_reason` (
                                              `trade_reason_id` bigint NOT NULL AUTO_INCREMENT COMMENT '이유 ID',
                                              `label` varchar(50) NOT NULL COMMENT '선택지 문구 (예: 나눠 담으려고)',
    `sort_order` int NOT NULL DEFAULT '0' COMMENT '정렬 순서',
    `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '사용 여부',
    PRIMARY KEY (`trade_reason_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='매매 이유';

-- trading_season 테이블 생성 (투자 시즌)
CREATE TABLE IF NOT EXISTS `trading_season` (
                                                `trading_season_id` bigint NOT NULL AUTO_INCREMENT COMMENT '시즌 ID',
                                                `name` varchar(50) NOT NULL COMMENT '시즌 이름 (예: 시즌 7)',
    `start_date` date NOT NULL COMMENT '시작일 (월요일)',
    `end_date` date NOT NULL COMMENT '종료일 (시작 + 13일, 랭킹 공개일)',
    `status` varchar(20) NOT NULL DEFAULT 'READY' COMMENT 'READY / RUNNING / HOLIDAY / SETTLING / ENDED',
    `current_turn` tinyint NOT NULL DEFAULT '0' COMMENT '현재 턴 0~12',
    `total_turns` tinyint NOT NULL DEFAULT '12' COMMENT '전체 턴',
    `unlock_level` tinyint NOT NULL DEFAULT '8' COMMENT '참가 해금 레벨',
    `carryover_rate` decimal(5,2) NOT NULL DEFAULT '30.00' COMMENT '이월 비율(%)',
    `carryover_cap` bigint DEFAULT NULL COMMENT '이월 상한 (미정)',
    `settled_at` datetime DEFAULT NULL COMMENT '정산 일시',
    `ranking_published_at` datetime DEFAULT NULL COMMENT '랭킹 공개 일시',
    `created_by` bigint NOT NULL COMMENT '만든 관리자',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    PRIMARY KEY (`trading_season_id`),
    KEY `fk_trading_season_created_by` (`created_by`),
    CONSTRAINT `fk_trading_season_created_by` FOREIGN KEY (`created_by`) REFERENCES `member` (`member_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='투자 시즌';

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

-- 외래키 검사 다시 켜기
SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- 시드 데이터 (로컬 개발용) ─ 운영 서버에는 넣지 않는다
-- =====================================================================
-- 모든 시드 계정의 비밀번호는 test1234 (BCrypt 해시로 저장)
-- 운영 관리자 계정은 시드가 아니라 ADMIN_INITIAL_* 환경변수로 만든다 (환경변수 문서 2-3)

-- ---------------------------------------------------------------------
-- [DBA ①] 회원·인증·운영·강의·퀴즈 구역
-- ---------------------------------------------------------------------

-- 회원: 1 관리자 / 2~6 강사 / 7~8 학습자 (7은 수강 이력 없음, 8은 이력 있음 용도)
-- 8(민지)은 해금 확인용으로 Lv.8·캐시를 넣어 두었다. 원장(cash_history·exp_history)은 DBA ② 구역에서 맞춘다
INSERT INTO member (member_id, email, password_hash, nickname, birth_date, role, status, level, exp, cash) VALUES
(1, 'admin@moneybeetle.local', '$2a$10$OKNSjtlUahwwv0zo9TcB9u7sZPPxwoo6duhvwtELedHblmBpqkzoK', '관리자', '1999-04-12', 'ADMIN', 'ACTIVE', 20, 6000, 0),
(2, 'seoyeon@moneybeetle.local', '$2a$10$OKNSjtlUahwwv0zo9TcB9u7sZPPxwoo6duhvwtELedHblmBpqkzoK', '박서연', '1999-04-12', 'INSTRUCTOR', 'ACTIVE', 1, 0, 0),
(3, 'dohyun@moneybeetle.local', '$2a$10$OKNSjtlUahwwv0zo9TcB9u7sZPPxwoo6duhvwtELedHblmBpqkzoK', '이도현', '1999-04-12', 'INSTRUCTOR', 'ACTIVE', 1, 0, 0),
(4, 'jisu@moneybeetle.local', '$2a$10$OKNSjtlUahwwv0zo9TcB9u7sZPPxwoo6duhvwtELedHblmBpqkzoK', '한지수', '1999-04-12', 'INSTRUCTOR', 'ACTIVE', 1, 0, 0),
(5, 'minjae@moneybeetle.local', '$2a$10$OKNSjtlUahwwv0zo9TcB9u7sZPPxwoo6duhvwtELedHblmBpqkzoK', '오민재', '1999-04-12', 'INSTRUCTOR', 'ACTIVE', 1, 0, 0),
(6, 'harin@moneybeetle.local', '$2a$10$OKNSjtlUahwwv0zo9TcB9u7sZPPxwoo6duhvwtELedHblmBpqkzoK', '정하린', '1999-04-12', 'INSTRUCTOR', 'ACTIVE', 1, 0, 0),
(7, 'newbie@moneybeetle.local', '$2a$10$OKNSjtlUahwwv0zo9TcB9u7sZPPxwoo6duhvwtELedHblmBpqkzoK', '새내기', '1999-04-12', 'MEMBER', 'ACTIVE', 1, 0, 0),
(8, 'minji@moneybeetle.local', '$2a$10$OKNSjtlUahwwv0zo9TcB9u7sZPPxwoo6duhvwtELedHblmBpqkzoK', '민지', '1999-04-12', 'MEMBER', 'ACTIVE', 8, 1250, 12000);

-- 강사 정보: 모두 승인 상태 (승인 관리자 = 1)
INSERT INTO instructor_profile (member_id, display_name, field, approval_status, approved_by, approved_at) VALUES
(2, '박서연', '예적금·청약', 'APPROVED', 1, '2026-11-01 10:00:00'),
(3, '이도현', '국내 주식·ETF', 'APPROVED', 1, '2026-11-01 10:00:00'),
(4, '한지수', '월급 관리', 'APPROVED', 1, '2026-11-01 10:00:00'),
(5, '오민재', '소비·위험 관리', 'APPROVED', 1, '2026-11-01 10:00:00'),
(6, '정하린', '미국 주식·분산 투자', 'APPROVED', 1, '2026-11-01 10:00:00');

-- 약관 (가입 화면용)
INSERT INTO terms (terms_id, title, content, is_required, version, effective_at) VALUES
(1, '서비스 이용약관', '서비스 이용약관 본문 (초안)', 1, '1.0', '2026-11-01 00:00:00'),
(2, '개인정보 수집·이용 동의', '개인정보 수집·이용 동의 본문 (초안)', 1, '1.0', '2026-11-01 00:00:00'),
(3, '마케팅 정보 수신 동의', '마케팅 정보 수신 동의 본문 (초안)', 0, '1.0', '2026-11-01 00:00:00');

-- 서비스 설정 (값은 정책 정의서 초안 기준. 관리자 화면에서 바꾼다)
INSERT INTO system_setting (setting_key, setting_value, description) VALUES
('QUIZ_PASS_SCORE', '70', '퀴즈 통과 점수 (SP-34)'),
('UNLOCK_LEVEL_TRADING', '8', '모의투자 해금 레벨 (SP-24)'),
('UNLOCK_LEVEL_HOUSING', '12', '모의 청약 해금 레벨 (SP-24)'),
('INQUIRY_DAILY_LIMIT', '10', '문의글 하루 작성 한도 (DP-30)'),
('LOGIN_MAX_FAIL', '5', '로그인 잠금 실패 횟수 (DP-10)'),
('LOGIN_LOCK_MINUTES', '10', '로그인 잠금 시간(분) (DP-10)');

-- 금칙어 (광고·외부 연락처 유도 예시. 실제 목록은 팀이 채운다)
INSERT INTO forbidden_word (word) VALUES ('오픈채팅'), ('텔레그램'), ('카톡문의'), ('무료리딩방');

-- 카테고리: 1~2 상위 / 11~15, 21~23 소주제 (화면 설계 5.2.3)
INSERT INTO category (category_id, parent_id, name, depth, sort_order) VALUES
(1, NULL, '돈 굴리기', 1, 1),
(2, NULL, '부동산', 1, 2),
(11, 1, '월급 쪼개기', 2, 1),
(12, 1, '예금·적금', 2, 2),
(13, 1, '국내 주식', 2, 3),
(14, 1, '미국 주식', 2, 4),
(15, 1, 'ETF·분산 투자', 2, 5),
(21, 2, '청약', 2, 1),
(22, 2, '전·월세', 2, 2),
(23, 2, '대출', 2, 3);
-- 전·월세(22)·대출(23)은 일부러 강의를 넣지 않는다 (빈 소주제 안내 화면 확인용)

-- 강의: 1~15 허가·공개 / 16 검수 중 / 17 반려 / 18 작성 중 / 19 허가 후 비공개 (COURSE_NOT_PUBLIC 확인용)
INSERT INTO course (course_id, instructor_id, category_id, title, description, status, is_public, info_base_date, info_source, submitted_at, approved_at) VALUES
(1, 4, 11, '통장 3개로 월급 나누기', '월급·생활비·비상금 통장으로 나누는 습관을 만들어요.', 'APPROVED', 1, NULL, NULL, '2026-11-02 10:00:00', '2026-11-03 10:00:00'),
(2, 4, 11, '비상금은 얼마나 모아야 할까', '비상금의 기준과 모으는 순서를 알아봐요.', 'APPROVED', 1, NULL, NULL, '2026-11-02 10:00:00', '2026-11-03 10:00:00'),
(3, 5, 11, '새는 고정비 줄이기', '구독료·통신비처럼 매달 빠져나가는 돈을 점검해요.', 'APPROVED', 1, NULL, NULL, '2026-11-02 10:00:00', '2026-11-03 10:00:00'),
(4, 2, 12, '예금과 적금, 뭐가 다를까', '목돈을 맡기는 예금과 나눠 모으는 적금의 차이를 배워요.', 'APPROVED', 1, NULL, NULL, '2026-11-02 10:00:00', '2026-11-03 10:00:00'),
(5, 2, 12, '이자는 어떻게 계산될까', '적금 이자가 생각보다 적은 이유를 계산으로 확인해요.', 'APPROVED', 1, NULL, NULL, '2026-11-02 10:00:00', '2026-11-03 10:00:00'),
(6, 5, 12, '중도해지하면 생기는 일', '적금을 깨면 이자가 어떻게 줄어드는지 알아봐요.', 'APPROVED', 1, NULL, NULL, '2026-11-02 10:00:00', '2026-11-03 10:00:00'),
(7, 2, 12, '청년 정책 적금 알아보기', '청년을 위한 정책 적금의 조건을 소개해요.', 'APPROVED', 1, '2026-10-01', '금융위원회 보도자료', '2026-11-02 10:00:00', '2026-11-03 10:00:00'),
(8, 3, 13, '주식은 회사의 조각이다', '주식이 무엇이고 왜 가격이 움직이는지 배워요.', 'APPROVED', 1, NULL, NULL, '2026-11-02 10:00:00', '2026-11-03 10:00:00'),
(9, 3, 13, '증권 계좌와 주문 방법', '증권 계좌를 만들고 주문하는 흐름을 익혀요.', 'APPROVED', 1, NULL, NULL, '2026-11-02 10:00:00', '2026-11-03 10:00:00'),
(10, 6, 14, '미국 주식을 사는 방법', '해외 주식 주문과 환전 흐름을 알아봐요.', 'APPROVED', 1, NULL, NULL, '2026-11-02 10:00:00', '2026-11-03 10:00:00'),
(11, 6, 14, '환율과 해외 주식 세금', '환율이 수익에 주는 영향과 세금을 배워요.', 'APPROVED', 1, NULL, NULL, '2026-11-02 10:00:00', '2026-11-03 10:00:00'),
(12, 3, 15, 'ETF와 펀드, 채권', '여러 자산에 나눠 담는 상품을 비교해요.', 'APPROVED', 1, NULL, NULL, '2026-11-02 10:00:00', '2026-11-03 10:00:00'),
(13, 6, 15, '분산 투자로 위험 줄이기', '한 바구니에 담지 않는 투자의 원리를 배워요.', 'APPROVED', 1, NULL, NULL, '2026-11-02 10:00:00', '2026-11-03 10:00:00'),
(14, 5, 15, '코인과 위험', '변동성이 큰 자산의 위험을 알아봐요.', 'APPROVED', 1, NULL, NULL, '2026-11-02 10:00:00', '2026-11-03 10:00:00'),
(15, 2, 21, '청약통장 기초와 가점 계산', '청약통장의 쓰임과 가점 계산 방법을 배워요.', 'APPROVED', 1, '2026-10-01', '청약홈', '2026-11-02 10:00:00', '2026-11-03 10:00:00'),
(16, 3, 13, '배당주 읽는 법', '배당과 배당수익률을 배워요.', 'REVIEW', 0, NULL, NULL, '2026-11-02 10:00:00', NULL),
(17, 5, 15, '투자 사기 유형', '흔한 투자 사기 수법을 알아봐요.', 'REJECTED', 0, NULL, NULL, '2026-11-02 10:00:00', NULL),
(18, 4, 11, '한 달 예산 짜기', '작성 중인 강의예요.', 'DRAFT', 0, NULL, NULL, NULL, NULL),
(19, 2, 12, '적금 금리 비교하기', '허가됐지만 비공개로 바꾼 강의예요.', 'APPROVED', 0, NULL, NULL, '2026-11-02 10:00:00', '2026-11-03 10:00:00');

-- 검수 이력: 허가된 강의와 반려된 강의 (검수 관리자 = 1)
INSERT INTO course_review (course_id, admin_id, result, reason) VALUES
(1, 1, 'APPROVED', NULL),
(2, 1, 'APPROVED', NULL),
(3, 1, 'APPROVED', NULL),
(4, 1, 'APPROVED', NULL),
(5, 1, 'APPROVED', NULL),
(6, 1, 'APPROVED', NULL),
(7, 1, 'APPROVED', NULL),
(8, 1, 'APPROVED', NULL),
(9, 1, 'APPROVED', NULL),
(10, 1, 'APPROVED', NULL),
(11, 1, 'APPROVED', NULL),
(12, 1, 'APPROVED', NULL),
(13, 1, 'APPROVED', NULL),
(14, 1, 'APPROVED', NULL),
(15, 1, 'APPROVED', NULL),
(19, 1, 'APPROVED', NULL),
(17, 1, 'REJECTED', '퀴즈 해설이 비어 있어요. 보완 후 다시 요청해 주세요.');

-- 영상·요약글: 영상은 파일 업로드로 확정(#30). 지금은 로컬 업로드 폴더 기준 예시 경로
INSERT INTO lesson (course_id, sort_order, title, video_url, duration_seconds, summary) VALUES
(1, 1, '왜 통장을 나눠야 할까', '/uploads/videos/course-1-1.mp4', 420, '왜 통장을 나눠야 할까 영상의 요약글이에요. (시드 예시)'),
(1, 2, '통장 3개 만드는 순서', '/uploads/videos/course-1-2.mp4', 540, '통장 3개 만드는 순서 영상의 요약글이에요. (시드 예시)'),
(2, 1, '비상금이 필요한 순간', '/uploads/videos/course-2-1.mp4', 360, '비상금이 필요한 순간 영상의 요약글이에요. (시드 예시)'),
(2, 2, '내 비상금 목표 정하기', '/uploads/videos/course-2-2.mp4', 480, '내 비상금 목표 정하기 영상의 요약글이에요. (시드 예시)'),
(3, 1, '고정비 찾아내기', '/uploads/videos/course-3-1.mp4', 400, '고정비 찾아내기 영상의 요약글이에요. (시드 예시)'),
(4, 1, '예금과 적금의 기본', '/uploads/videos/course-4-1.mp4', 480, '예금과 적금의 기본 영상의 요약글이에요. (시드 예시)'),
(4, 2, '단리와 복리', '/uploads/videos/course-4-2.mp4', 600, '단리와 복리 영상의 요약글이에요. (시드 예시)'),
(4, 3, '이자에 붙는 세금', '/uploads/videos/course-4-3.mp4', 420, '이자에 붙는 세금 영상의 요약글이에요. (시드 예시)'),
(5, 1, '적금 이자 계산법', '/uploads/videos/course-5-1.mp4', 540, '적금 이자 계산법 영상의 요약글이에요. (시드 예시)'),
(6, 1, '중도해지 이율', '/uploads/videos/course-6-1.mp4', 450, '중도해지 이율 영상의 요약글이에요. (시드 예시)'),
(7, 1, '정책 적금이란', '/uploads/videos/course-7-1.mp4', 500, '정책 적금이란 영상의 요약글이에요. (시드 예시)'),
(8, 1, '주식의 뜻', '/uploads/videos/course-8-1.mp4', 420, '주식의 뜻 영상의 요약글이에요. (시드 예시)'),
(8, 2, '주가가 움직이는 이유', '/uploads/videos/course-8-2.mp4', 560, '주가가 움직이는 이유 영상의 요약글이에요. (시드 예시)'),
(9, 1, '증권 계좌 만들기', '/uploads/videos/course-9-1.mp4', 380, '증권 계좌 만들기 영상의 요약글이에요. (시드 예시)'),
(9, 2, '매수·매도 주문', '/uploads/videos/course-9-2.mp4', 520, '매수·매도 주문 영상의 요약글이에요. (시드 예시)'),
(10, 1, '해외 주식 주문', '/uploads/videos/course-10-1.mp4', 500, '해외 주식 주문 영상의 요약글이에요. (시드 예시)'),
(11, 1, '환율과 수익', '/uploads/videos/course-11-1.mp4', 460, '환율과 수익 영상의 요약글이에요. (시드 예시)'),
(11, 2, '해외 주식 세금', '/uploads/videos/course-11-2.mp4', 520, '해외 주식 세금 영상의 요약글이에요. (시드 예시)'),
(12, 1, 'ETF란', '/uploads/videos/course-12-1.mp4', 480, 'ETF란 영상의 요약글이에요. (시드 예시)'),
(12, 2, '펀드와 채권', '/uploads/videos/course-12-2.mp4', 540, '펀드와 채권 영상의 요약글이에요. (시드 예시)'),
(13, 1, '분산 투자의 원리', '/uploads/videos/course-13-1.mp4', 500, '분산 투자의 원리 영상의 요약글이에요. (시드 예시)'),
(14, 1, '코인의 변동성', '/uploads/videos/course-14-1.mp4', 450, '코인의 변동성 영상의 요약글이에요. (시드 예시)'),
(15, 1, '청약통장이 뭐예요?', '/uploads/videos/course-15-1.mp4', 480, '청약통장이 뭐예요? 영상의 요약글이에요. (시드 예시)'),
(15, 2, '가점은 어떻게 계산할까', '/uploads/videos/course-15-2.mp4', 840, '가점은 어떻게 계산할까 영상의 요약글이에요. (시드 예시)'),
(16, 1, '배당이란', '/uploads/videos/course-16-1.mp4', 400, '배당이란 영상의 요약글이에요. (시드 예시)'),
(17, 1, '리딩방과 원금 보장', '/uploads/videos/course-17-1.mp4', 420, '리딩방과 원금 보장 영상의 요약글이에요. (시드 예시)'),
(19, 1, '금리 비교 기준', '/uploads/videos/course-19-1.mp4', 400, '금리 비교 기준 영상의 요약글이에요. (시드 예시)');

-- 퀴즈: 강의 4(예금과 적금), 15(청약통장)에만 전체 문항을 넣었다 (테스트 퀴즈 OX 5 + 퀴즈 4지선다 10)
-- 나머지 강의의 퀴즈는 강사 화면(#30)이 완성되면 강사가 채운다
INSERT INTO quiz_question (quiz_question_id, course_id, quiz_type, question_type, content, explanation, sort_order) VALUES
(1, 4, 'TEST', 'OX', '예금은 목돈을 한 번에 맡기는 상품이다.', '예금은 정해진 기간 동안 목돈을 한 번에 맡기는 상품이에요.', 1),
(2, 4, 'TEST', 'OX', '적금은 정해진 기간 동안 돈을 나눠서 넣는 상품이다.', '적금은 매달 일정 금액을 나눠 넣는 상품이에요.', 2),
(3, 4, 'TEST', 'OX', '금리가 같다면 적금 이자가 예금 이자보다 항상 많다.', '적금은 나눠 넣은 돈마다 맡긴 기간이 달라 같은 금리라도 이자가 더 적어요.', 3),
(4, 4, 'TEST', 'OX', '예금자보호는 금융회사별로 정해진 한도까지 원금과 이자를 보호한다.', '예금자보호는 금융회사별로 한도 안에서 원금과 이자를 보호해요.', 4),
(5, 4, 'TEST', 'OX', '적금을 중도해지해도 약정한 금리를 그대로 받는다.', '중도해지하면 약정 금리보다 낮은 중도해지 이율이 적용돼요.', 5),
(6, 4, 'MAIN', 'CHOICE', '목돈 1,000만 원을 1년 동안 맡겨 두고 싶을 때 알맞은 상품은?', '목돈을 한 번에 맡기는 상품은 정기예금이에요.', 1),
(7, 4, 'MAIN', 'CHOICE', '매달 30만 원씩 꾸준히 모으고 싶을 때 알맞은 상품은?', '매달 나눠 넣는 상품은 정기적금이에요.', 2),
(8, 4, 'MAIN', 'CHOICE', '단리에 대한 설명으로 옳은 것은?', '단리는 원금에만 이자가 붙는 방식이에요.', 3),
(9, 4, 'MAIN', 'CHOICE', '복리에 대한 설명으로 옳은 것은?', '복리는 원금과 쌓인 이자에 함께 이자가 붙어요.', 4),
(10, 4, 'MAIN', 'CHOICE', '1,000만 원을 연 3% 단리 예금에 1년 맡기면 세금 전 이자는?', '1,000만 원 × 3% = 30만 원이에요.', 5),
(11, 4, 'MAIN', 'CHOICE', '예금·적금 이자에 붙는 일반 이자소득세율(지방소득세 포함)은?', '일반과세는 이자소득세 14%와 지방소득세 1.4%를 합친 15.4%예요.', 6),
(12, 4, 'MAIN', 'CHOICE', '연 4% 적금인데 실제 받는 이자가 예상보다 적은 이유는?', '나중에 넣은 돈일수록 맡긴 기간이 짧아 이자가 적게 붙어요.', 7),
(13, 4, 'MAIN', 'CHOICE', '적금을 만기 전에 해지하면 보통 어떻게 될까?', '중도해지하면 낮은 중도해지 이율이 적용돼요.', 8),
(14, 4, 'MAIN', 'CHOICE', '다음 중 예금자보호 대상이 아닌 것은?', '펀드 같은 투자 상품은 예금자보호 대상이 아니에요.', 9),
(15, 4, 'MAIN', 'CHOICE', '비상금을 두기에 가장 알맞은 곳은?', '비상금은 바로 꺼내 쓸 수 있는 곳에 두는 게 좋아요.', 10),
(16, 15, 'TEST', 'OX', '주택청약종합저축은 공공주택과 민영주택 청약에 모두 쓸 수 있다.', '주택청약종합저축은 공공·민영 주택 청약에 모두 쓸 수 있어요.', 1),
(17, 15, 'TEST', 'OX', '청약 가점제에서는 점수가 높을수록 당첨에 유리하다.', '가점제는 점수가 높은 순서로 당첨자를 정해요.', 2),
(18, 15, 'TEST', 'OX', '청약 가점 항목에는 무주택 기간, 부양가족 수, 청약통장 가입 기간이 있다.', '가점은 이 세 항목으로 계산해요.', 3),
(19, 15, 'TEST', 'OX', '이미 집을 가지고 있어도 무주택 기간 점수를 받을 수 있다.', '무주택 기간 점수는 집이 없는 기간에 대해 받아요.', 4),
(20, 15, 'TEST', 'OX', 'MoneyBeetle 모의 청약 결과는 실제 청약에도 반영된다.', '모의 청약은 가상 실습이라 실제 청약과 관계가 없어요.', 5),
(21, 15, 'MAIN', 'CHOICE', '청약 가점제의 만점은?', '무주택 32점 + 부양가족 35점 + 통장 가입 기간 17점 = 84점이에요.', 1),
(22, 15, 'MAIN', 'CHOICE', '무주택 기간 항목의 최고 점수는?', '무주택 기간은 최고 32점이에요.', 2),
(23, 15, 'MAIN', 'CHOICE', '부양가족 수 항목의 최고 점수는?', '부양가족 수는 최고 35점이에요.', 3),
(24, 15, 'MAIN', 'CHOICE', '청약통장 가입 기간 항목의 최고 점수는?', '청약통장 가입 기간은 최고 17점이에요.', 4),
(25, 15, 'MAIN', 'CHOICE', '다음 중 청약 가점 항목이 아닌 것은?', '연봉은 가점 항목이 아니에요.', 5),
(26, 15, 'MAIN', 'CHOICE', '공공분양 청약에서 월 납입 인정액의 상한은? (2024년 11월부터)', '2024년 11월부터 월 납입 인정액이 10만 원에서 25만 원으로 올랐어요.', 6),
(27, 15, 'MAIN', 'CHOICE', '무주택 기간이 길어지면 가점은 어떻게 될까?', '무주택 기간이 길수록 점수가 높아져요.', 7),
(28, 15, 'MAIN', 'CHOICE', '100세대 공급에 400명이 신청했다면 경쟁률은?', '경쟁률 = 신청자 수 ÷ 공급 세대 수 = 400 ÷ 100 = 4:1이에요.', 8),
(29, 15, 'MAIN', 'CHOICE', 'MoneyBeetle 모의 청약에서 같은 단지에 신청할 수 있는 횟수는?', '같은 단지에는 한 번만 신청할 수 있어요.', 9),
(30, 15, 'MAIN', 'CHOICE', '당첨 뒤에 자금 계획이 필요한 가장 큰 이유는?', '당첨돼도 정해진 날에 낼 돈이 없으면 계약을 포기해야 해요.', 10);

INSERT INTO quiz_choice (quiz_question_id, content, is_correct, sort_order) VALUES
(1, 'O', 1, 1),
(1, 'X', 0, 2),
(2, 'O', 1, 1),
(2, 'X', 0, 2),
(3, 'O', 0, 1),
(3, 'X', 1, 2),
(4, 'O', 1, 1),
(4, 'X', 0, 2),
(5, 'O', 0, 1),
(5, 'X', 1, 2),
(6, '정기예금', 1, 1),
(6, '정기적금', 0, 2),
(6, '주식', 0, 3),
(6, '코인', 0, 4),
(7, '정기예금', 0, 1),
(7, '정기적금', 1, 2),
(7, '청약 당첨', 0, 3),
(7, '신용카드', 0, 4),
(8, '원금에만 이자가 붙는다', 1, 1),
(8, '이자에도 이자가 붙는다', 0, 2),
(8, '이자가 붙지 않는다', 0, 3),
(8, '세금이 없다', 0, 4),
(9, '원금에만 이자가 붙는다', 0, 1),
(9, '원금과 그동안 붙은 이자에 함께 이자가 붙는다', 1, 2),
(9, '이자를 매달 돌려준다', 0, 3),
(9, '금리가 항상 높다', 0, 4),
(10, '3만 원', 0, 1),
(10, '30만 원', 1, 2),
(10, '300만 원', 0, 3),
(10, '3,000원', 0, 4),
(11, '5.5%', 0, 1),
(11, '9.9%', 0, 2),
(11, '15.4%', 1, 3),
(11, '22%', 0, 4),
(12, '은행이 이자를 덜 줘서', 0, 1),
(12, '매달 넣은 돈마다 맡긴 기간이 달라서', 1, 2),
(12, '적금에는 이자가 없어서', 0, 3),
(12, '세금이 50%라서', 0, 4),
(13, '약정 금리를 그대로 받는다', 0, 1),
(13, '약정보다 낮은 중도해지 이율을 받는다', 1, 2),
(13, '원금을 잃는다', 0, 3),
(13, '보너스를 받는다', 0, 4),
(14, '정기예금', 0, 1),
(14, '정기적금', 0, 2),
(14, '보통예금', 0, 3),
(14, '주식형 펀드', 1, 4),
(15, '입출금이 자유로운 통장', 1, 1),
(15, '만기 3년 적금', 0, 2),
(15, '주식', 0, 3),
(15, '코인', 0, 4),
(16, 'O', 1, 1),
(16, 'X', 0, 2),
(17, 'O', 1, 1),
(17, 'X', 0, 2),
(18, 'O', 1, 1),
(18, 'X', 0, 2),
(19, 'O', 0, 1),
(19, 'X', 1, 2),
(20, 'O', 0, 1),
(20, 'X', 1, 2),
(21, '64점', 0, 1),
(21, '74점', 0, 2),
(21, '84점', 1, 3),
(21, '100점', 0, 4),
(22, '17점', 0, 1),
(22, '32점', 1, 2),
(22, '35점', 0, 3),
(22, '40점', 0, 4),
(23, '17점', 0, 1),
(23, '25점', 0, 2),
(23, '32점', 0, 3),
(23, '35점', 1, 4),
(24, '10점', 0, 1),
(24, '17점', 1, 2),
(24, '20점', 0, 3),
(24, '32점', 0, 4),
(25, '무주택 기간', 0, 1),
(25, '부양가족 수', 0, 2),
(25, '청약통장 가입 기간', 0, 3),
(25, '연봉', 1, 4),
(26, '10만 원', 0, 1),
(26, '20만 원', 0, 2),
(26, '25만 원', 1, 3),
(26, '50만 원', 0, 4),
(27, '낮아진다', 0, 1),
(27, '높아진다', 1, 2),
(27, '변하지 않는다', 0, 3),
(27, '0점이 된다', 0, 4),
(28, '1:1', 0, 1),
(28, '2:1', 0, 2),
(28, '4:1', 1, 3),
(28, '40:1', 0, 4),
(29, '1번', 1, 1),
(29, '2번', 0, 2),
(29, '3번', 0, 3),
(29, '제한 없음', 0, 4),
(30, '계약금·중도금·잔금을 정해진 날에 내야 해서', 1, 1),
(30, '청약통장이 사라져서', 0, 2),
(30, '가점이 바뀌어서', 0, 3),
(30, '세금이 없어져서', 0, 4);

-- 학습자 8(민지)의 학습 기록: 홈 '이어서 볼 강의'와 vr1/vr2 분기 확인용
INSERT INTO learning_progress (member_id, course_id, status, last_lesson_id, last_position_seconds, completed_at) VALUES
(8, 4, 'COMPLETED', NULL, 0, '2026-11-04 21:00:00'),
(8, 15, 'IN_PROGRESS', NULL, 312, NULL);
UPDATE learning_progress lp
JOIN lesson l ON l.course_id = lp.course_id AND l.sort_order = 2
SET lp.last_lesson_id = l.lesson_id
WHERE lp.member_id = 8 AND lp.course_id = 15;

INSERT INTO scrap (member_id, course_id) VALUES (8, 15), (8, 12);

-- ---------------------------------------------------------------------
-- [DBA ②] 성장·알림·경제 용어·모의투자 구역 (DBA ②가 이 아래에 추가)
-- 청약통장(subscription_account)은 회원가입 때 자동 개설되므로, 시드 회원 1~8의 통장도 이 구역에서 만든다
-- ---------------------------------------------------------------------

SHOW TABLES;