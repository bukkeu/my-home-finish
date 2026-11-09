-- 이슈 #75: 경제 용어·날짜별 오늘의 용어·알림 샘플 시드 (이미 시드를 넣어 둔 DB용)
-- 새로 만드는 DB 는 sample-data.sql 에 같은 값이 들어 있으므로 이 파일을 실행하지 않는다.
-- 기존 시드가 있는 격리된 로컬 DB 에서만 실행한다 (SQL 이 로컬 DB 인지 판별하지는 않는다).
-- 여러 번 실행해도 안전하다: 용어·일정은 이미 있으면 건너뛰고(INSERT IGNORE, 용어는 이름 기준), 알림은 샘플 행마다 같은 알림이 있는지 보고 없는 것만 넣는다.

-- 용어는 번호를 정하지 않고 용어 이름으로 넣는다 (term 이 고유라서 이미 있는 이름은 건너뛴다).
-- 이미 다른 용어가 1~12번을 쓰고 있어도 번호가 겹치지 않고, 번호는 AUTO_INCREMENT 가 입력 순서대로 매긴다 (등록 순서 = 번호 순서)
INSERT IGNORE INTO economic_term (term, description) VALUES
('복리', '원금에 붙은 이자에도 다시 이자가 붙는 방식'),
('실수령액', '세전 급여에서 4대보험과 소득세를 뺀 금액'),
('배당', '기업이 이익의 일부를 주주에게 나눠 주는 것'),
('리밸런싱', '정해 둔 자산 비중으로 포트폴리오를 다시 맞추는 일'),
('청약 가점', '무주택 기간·부양가족·통장 가입 기간으로 매기는 점수'),
('ETF', '거래소에 상장돼 주식처럼 사고파는 지수 추종 펀드'),
('확정일자', '임대차 계약서에 날짜를 받아 보증금 우선순위를 확보하는 것'),
('LTV', '집값 대비 대출 가능 금액의 비율'),
('분산 투자', '여러 자산에 나눠 위험을 줄이는 투자'),
('인플레이션', '물가가 계속 오르고 돈의 가치가 떨어지는 현상'),
('예금자보호', '금융사가 문을 닫아도 1인당 일정 금액까지 돌려주는 제도'),
('전세가율', '매매가 대비 전세 보증금의 비율');

-- 오늘·내일 중 자동 선정만 있고 5개가 안 되는 날은 먼저 비운다.
-- 용어가 5개 미만일 때 홈을 열면 자동 선정이 있는 만큼만(예: 1개) 저장해 두는데, 그 날짜를 아래에서 통째로 건너뛰면 5개가 채워지지 않기 때문이다.
-- 관리자가 직접 지정한 행(is_auto = 0)이 하나라도 있는 날과 이미 5개 이상인 날은 건드리지 않는다.
-- (같은 테이블을 읽으며 지우려면 하위 질의를 한 번 감싸야 한다 - MySQL 규칙)
DELETE FROM term_schedule
WHERE show_date IN (
    SELECT d.show_date FROM (
        SELECT show_date FROM term_schedule
        WHERE show_date IN (CURDATE(), CURDATE() + INTERVAL 1 DAY)
        GROUP BY show_date
        HAVING SUM(is_auto = 0) = 0 AND COUNT(*) < 5
    ) d
);

-- 오늘(실행한 날)과 내일의 오늘의 경제 용어. 용어 번호를 믿지 않고 이름으로 실제 번호를 찾아 넣는다.
-- 날짜 단위로 통째로 넣거나 통째로 건너뛴다: 그 날짜에 일정이 하나라도 이미 있으면(관리자가 지정했거나 자동 선정으로 저장된 날) 그 날짜는 건드리지 않는다.
-- 그래서 기존 일정과 일부만 겹쳐 5개가 아닌 4개짜리 날이 생기는 일이 없다 (INSERT IGNORE 는 겹치는 행만 건너뛴다).
INSERT IGNORE INTO term_schedule (show_date, economic_term_id, sort_order, is_auto)
SELECT CURDATE() + INTERVAL s.day_offset DAY, t.economic_term_id, s.sort_order, 0
FROM (
    SELECT 0 AS day_offset, '복리' AS term, 1 AS sort_order
    UNION ALL SELECT 0, '실수령액', 2
    UNION ALL SELECT 0, '배당', 3
    UNION ALL SELECT 0, '리밸런싱', 4
    UNION ALL SELECT 0, '청약 가점', 5
    UNION ALL SELECT 1, 'ETF', 1
    UNION ALL SELECT 1, '확정일자', 2
    UNION ALL SELECT 1, 'LTV', 3
    UNION ALL SELECT 1, '분산 투자', 4
    UNION ALL SELECT 1, '인플레이션', 5
) s
JOIN economic_term t ON t.term = s.term
WHERE NOT EXISTS (
    SELECT 1 FROM term_schedule x WHERE x.show_date = CURDATE() + INTERVAL s.day_offset DAY
);

-- 알림 샘플 (시드 회원 8 민지 4건, 강사 5번 1건).
-- - 번호가 같다고 시드 회원이라고 믿지 않는다: 회원 번호와 시드 이메일이 모두 맞는 회원에게만 넣는다.
--   (시드가 아닌 DB 에서 8번·5번이 실제 회원이어도 샘플 알림이 붙지 않는다)
-- - 샘플마다 같은 회원·종류·제목·이동 주소의 알림이 이미 있으면 그 행만 건너뛴다
INSERT INTO notification (member_id, type, title, link_url, is_read, created_at)
SELECT v.member_id, v.type, v.title, v.link_url, v.is_read, v.created_at
FROM (
    SELECT 8 AS member_id, 'minji@moneybeetle.local' AS seed_email, 'INQUIRY_REPLY' AS type, '문의에 답변이 등록됐어요' AS title, '/user/inquiry/1' AS link_url, 0 AS is_read, NOW() - INTERVAL 5 MINUTE AS created_at
    UNION ALL SELECT 8, 'minji@moneybeetle.local', 'RANKING', '시즌 6 랭킹이 공개됐어요', '/user/demo-trading', 0, NOW() - INTERVAL 3 HOUR
    UNION ALL SELECT 8, 'minji@moneybeetle.local', 'HOUSING_RESULT', '머니숲 리버뷰 청약 결과가 발표됐어요', '/user/demo-housing-subscription', 0, NOW() - INTERVAL 1 DAY
    UNION ALL SELECT 8, 'minji@moneybeetle.local', 'UNLOCK', '모의투자가 열렸어요', '/user/demo-trading', 1, NOW() - INTERVAL 2 DAY
    UNION ALL SELECT 5, 'minjae@moneybeetle.local', 'COURSE_REJECTED', '강의 검수 결과: 반려됐어요', '/instructor/courses/17', 0, NOW() - INTERVAL 1 HOUR
) v
JOIN member m ON m.member_id = v.member_id AND m.email = v.seed_email
WHERE NOT EXISTS (
    SELECT 1 FROM notification n
    WHERE n.member_id = v.member_id AND n.type = v.type AND n.title = v.title AND n.link_url = v.link_url
);
