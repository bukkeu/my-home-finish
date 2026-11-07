-- 전체 테이블 생성 후 시드 넣기 (mysql 클라이언트의 SOURCE 사용, sql 폴더에서 실행)
--   cd moneybeetle/src/main/resources/sql
--   mysql -u wanted -p < create_database.sql
--   mysql -u wanted -p money_beetle -e "SOURCE create_all.sql"
-- 시드는 로컬 개발용이므로 운영 서버에는 넣지 않는다 (seed/ 폴더는 맨 아래에서 따로 SOURCE)
USE money_beetle;

SET FOREIGN_KEY_CHECKS = 0;
SOURCE tables/admin_log.sql;
SOURCE tables/attendance.sql;
SOURCE tables/cash_history.sql;
SOURCE tables/category.sql;
SOURCE tables/course.sql;
SOURCE tables/course_review.sql;
SOURCE tables/economic_term.sql;
SOURCE tables/exp_history.sql;
SOURCE tables/forbidden_word.sql;
SOURCE tables/holding.sql;
SOURCE tables/housing_application.sql;
SOURCE tables/housing_complex.sql;
SOURCE tables/inquiry.sql;
SOURCE tables/inquiry_reply.sql;
SOURCE tables/instructor_profile.sql;
SOURCE tables/learning_progress.sql;
SOURCE tables/lesson.sql;
SOURCE tables/lesson_watch.sql;
SOURCE tables/level_policy.sql;
SOURCE tables/member.sql;
SOURCE tables/member_terms_agreement.sql;
SOURCE tables/news.sql;
SOURCE tables/news_tag.sql;
SOURCE tables/notification.sql;
SOURCE tables/password_reset_token.sql;
SOURCE tables/quiz_answer.sql;
SOURCE tables/quiz_attempt.sql;
SOURCE tables/quiz_choice.sql;
SOURCE tables/quiz_question.sql;
SOURCE tables/ranking_award.sql;
SOURCE tables/reward_rule.sql;
SOURCE tables/scrap.sql;
SOURCE tables/season_participant.sql;
SOURCE tables/season_turn.sql;
SOURCE tables/sector.sql;
SOURCE tables/social_account.sql;
SOURCE tables/stock.sql;
SOURCE tables/stock_price.sql;
SOURCE tables/subscription_account.sql;
SOURCE tables/subscription_deposit.sql;
SOURCE tables/system_setting.sql;
SOURCE tables/term_schedule.sql;
SOURCE tables/terms.sql;
SOURCE tables/trade_order.sql;
SOURCE tables/trade_reason.sql;
SOURCE tables/trading_season.sql;
SOURCE tables/wrong_answer_note.sql;
SET FOREIGN_KEY_CHECKS = 1;

-- 시드 (로컬 개발용)
SOURCE seed/seed_member_course.sql;

SHOW TABLES;
