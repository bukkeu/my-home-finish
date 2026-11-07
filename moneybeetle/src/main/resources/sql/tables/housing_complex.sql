-- housing_complex 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS housing_complex;

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

SET FOREIGN_KEY_CHECKS = 1;
