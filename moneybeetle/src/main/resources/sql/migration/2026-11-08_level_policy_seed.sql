USE money_beetle;

-- 레벨 정책 초기 데이터 등록 및 기존 데이터 검증
-- 기존 값이 확정 정책과 다르면 UNIQUE 제약 위반으로 실행을 중단한다.
-- 기존 값이 일치하면 누락된 레벨만 추가한다.

INSERT INTO level_policy
(level, required_exp, title, levelup_cash)
SELECT
    expected.level,
    expected.required_exp,
    expected.title,
    expected.levelup_cash
FROM (
         SELECT 1 AS level, 0 AS required_exp, '고시원' AS title, 0 AS levelup_cash
         UNION ALL SELECT 2, 150, '고시원', 20000
         UNION ALL SELECT 3, 300, '고시원', 30000
         UNION ALL SELECT 4, 450, '원룸', 40000
         UNION ALL SELECT 5, 650, '원룸', 50000
         UNION ALL SELECT 6, 850, '원룸', 60000
         UNION ALL SELECT 7, 1050, '원룸', 70000
         UNION ALL SELECT 8, 1250, '투룸', 80000
         UNION ALL SELECT 9, 1550, '투룸', 90000
         UNION ALL SELECT 10, 1850, '투룸', 100000
         UNION ALL SELECT 11, 2150, '투룸', 110000
         UNION ALL SELECT 12, 2450, '아파트', 120000
         UNION ALL SELECT 13, 2850, '아파트', 130000
         UNION ALL SELECT 14, 3250, '아파트', 140000
         UNION ALL SELECT 15, 3650, '아파트', 150000
         UNION ALL SELECT 16, 4050, '아파트', 160000
         UNION ALL SELECT 17, 4450, '내 집', 170000
         UNION ALL SELECT 18, 5050, '내 집', 180000
         UNION ALL SELECT 19, 5650, '내 집', 190000
         UNION ALL SELECT 20, 6250, '내 집', 200000
     ) AS expected
         LEFT JOIN level_policy AS actual
                   ON actual.level = expected.level
WHERE actual.level IS NULL
   OR actual.required_exp <> expected.required_exp
   OR actual.title <> expected.title
   OR actual.levelup_cash <> expected.levelup_cash;
