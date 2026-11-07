-- trade_reason 테이블 (한 테이블 = 한 파일). 단독으로 다시 실행할 수 있다(기존 테이블과 데이터는 삭제된다)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS trade_reason;

-- trade_reason 테이블 생성 (매매 이유)
CREATE TABLE IF NOT EXISTS `trade_reason` (
                                              `trade_reason_id` bigint NOT NULL AUTO_INCREMENT COMMENT '이유 ID',
                                              `label` varchar(50) NOT NULL COMMENT '선택지 문구 (예: 나눠 담으려고)',
    `sort_order` int NOT NULL DEFAULT '0' COMMENT '정렬 순서',
    `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '사용 여부',
    PRIMARY KEY (`trade_reason_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='매매 이유';

SET FOREIGN_KEY_CHECKS = 1;
