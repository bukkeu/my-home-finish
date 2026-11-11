-- 이슈 #205: 출석 경험치 지급으로 exp_history.source_type 에 ATTENDANCE 가 추가됐다 (컬럼 설명만 바꾸며 데이터·구조는 그대로)
ALTER TABLE exp_history
    MODIFY COLUMN `source_type` varchar(30) NOT NULL COMMENT 'COURSE_COMPLETE / QUIZ_PERFECT / ATTENDANCE';
