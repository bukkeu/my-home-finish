CREATE DATABASE IF NOT EXISTS money_beetle
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SHOW DATABASES;

-- 접속 계정은 각자 로컬 MySQL에서 한 번만 만든다. 비밀번호는 이 파일에 적지 않는다.
-- (아래 세 줄의 앞 `-- `를 지우고 <비밀번호>를 각자 정한 값으로 바꿔 실행. 환경변수 DB_USERNAME에는 계정명 moneybeetle을, DB_PASSWORD에는 여기서 정한 비밀번호를 넣는다)
-- CREATE USER IF NOT EXISTS 'moneybeetle'@'localhost' IDENTIFIED BY '<비밀번호>';
-- GRANT ALL PRIVILEGES ON money_beetle.* TO 'moneybeetle'@'localhost';
-- SHOW GRANTS FOR 'moneybeetle'@'localhost';
