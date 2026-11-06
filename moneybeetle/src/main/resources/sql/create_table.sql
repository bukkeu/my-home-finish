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

SHOW TABLES;