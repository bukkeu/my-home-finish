USE money_beetle;

-- 로그인 실패 횟수 컬럼을 TINYINT(최대 127) 에서 INT 로 넓힌다 (이슈 #6).
-- 로그인 잠금 정책(DP-10)이 "5회마다 잠금, 실패 횟수는 로그인에 성공할 때만 0 으로 초기화"라서
-- 잠금이 풀린 뒤에도 실패가 계속 쌓이면 127 을 넘을 수 있다. 넘으면 UPDATE 가 범위 오류로 실패해 로그인 실패 응답이 500 이 된다.
-- 값은 그대로 유지되고, 이미 INT 인 DB 에서 다시 실행해도 결과가 같다.
ALTER TABLE member
    MODIFY COLUMN failed_login_count int NOT NULL DEFAULT 0 COMMENT '연속 로그인 실패 횟수 (성공하면 0, 5회마다 잠금)';
