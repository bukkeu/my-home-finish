USE money_beetle;

-- 청약통장 납입 한도를 운영 설정으로 (이슈 #327, SP-70)
-- 1회 최소·최대 납입액, 하루 인정금액 한도를 system_setting 에서 읽는다. 관리자 운영 설정(API 112)에서 바꿀 수 있다.
-- 이미 있는 키는 관리자가 값을 고쳤을 수 있어서 바꾸지 않고, 없을 때만 넣는다. 다시 실행해도 결과가 같다.
-- 새로 DB를 만드는 사람은 sample-data.sql 에 이미 들어 있으므로 이 파일을 실행할 필요가 없다.

INSERT INTO system_setting (setting_key, setting_value, description)
SELECT 'DEPOSIT_MIN_AMOUNT', '20000', '청약통장 1회 최소 납입액 (SP-70, #327)'
WHERE NOT EXISTS (SELECT 1 FROM system_setting WHERE setting_key = 'DEPOSIT_MIN_AMOUNT');

INSERT INTO system_setting (setting_key, setting_value, description)
SELECT 'DEPOSIT_MAX_AMOUNT', '500000', '청약통장 1회 최대 납입액 (SP-70, #327)'
WHERE NOT EXISTS (SELECT 1 FROM system_setting WHERE setting_key = 'DEPOSIT_MAX_AMOUNT');

INSERT INTO system_setting (setting_key, setting_value, description)
SELECT 'DEPOSIT_DAILY_RECOGNIZED_LIMIT', '250000', '청약통장 하루 인정금액 한도 (SP-70, #327)'
WHERE NOT EXISTS (SELECT 1 FROM system_setting WHERE setting_key = 'DEPOSIT_DAILY_RECOGNIZED_LIMIT');
