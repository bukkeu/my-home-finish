CREATE DATABASE IF NOT EXISTS money_beetle
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SHOW DATABASES;
GRANT ALL PRIVILEGES ON money_beetle.* TO 'wanted'@'%';

SHOW GRANTS FOR 'wanted'@'%';