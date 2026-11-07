-- member 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS member;

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

SET FOREIGN_KEY_CHECKS = 1;
