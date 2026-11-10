USE money_beetle;

-- 시드 강의 영상을 실제로 재생되는 시연용 샘플 영상으로 바꾼다 (이슈 #73)
-- - 영상 파일: moneybeetle/uploads/videos/sample-1~3.mp4 (각 30초, 저장소에 포함). 영상 순서(sort_order)대로 함께 쓴다
-- - 길이를 실제 파일 길이 30초로 맞춘다 → 영상 완료(본 시간 90% 이상, #20)·퀴즈 열기·통과까지 바로 시연할 수 있다
-- 예전 시드 경로(/uploads/videos/course-?-?.mp4)인 영상만 바꾸고, 강사가 올린 영상은 건드리지 않는다. 다시 실행해도 결과가 같다.
-- 새로 DB를 만드는 사람은 sample-data.sql 에 이미 들어 있으므로 이 파일을 실행할 필요가 없다.

UPDATE lesson
SET video_url = CONCAT('/uploads/videos/sample-', sort_order, '.mp4'),
    duration_seconds = 30
WHERE video_url LIKE '/uploads/videos/course-%.mp4';

-- 시드 회원(8번)의 이어보기 위치가 새 길이(30초)를 넘지 않게 맞춘다
UPDATE learning_progress lp
JOIN lesson l ON l.lesson_id = lp.last_lesson_id
SET lp.last_position_seconds = LEAST(lp.last_position_seconds, l.duration_seconds)
WHERE lp.last_position_seconds > l.duration_seconds;
