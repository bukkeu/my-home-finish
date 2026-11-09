package com.wanted.moneybeetle.domain.growth.model.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.domain.growth.model.entity.RewardRule;
import com.wanted.moneybeetle.domain.growth.model.service.AttendanceService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   연속 출석 보상 규칙(ATTENDANCE_STREAK) 시드가 있는지 확인한다 (이슈 #12). 시드는 DB 에 넣어야 돌아가서 테스트가 직접 실행하지 못하므로
*   sample-data.sql 과 마이그레이션 파일의 값을 읽어 확정값(연속 5일째부터 10,000캐시, 경험치 없음, 사용 중)과 비교한다.
*   규칙이 없으면 5일째 출석 때 캐시 지급이 REWARD_RULE_NOT_FOUND 로 실패한다.
*  */
class AttendanceRewardSeedTest {

    private static String read(String path) throws Exception {
        try (InputStream in = AttendanceRewardSeedTest.class.getResourceAsStream(path)) {
            assertThat(in).as("클래스패스에 " + path + " 가 있어야 한다").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("sample-data.sql 에 ATTENDANCE_STREAK 규칙이 확정값(캐시 10,000, 경험치 0, 사용 중)으로 있다")
    void sampleDataHasRule() throws Exception {
        String sql = read("/sql/sample-data.sql");

        Matcher m = Pattern.compile("\\('" + RewardRule.ATTENDANCE_STREAK + "',\\s*'[^']+',\\s*(\\d+),\\s*(\\d+),\\s*'[^']*',\\s*(\\d)\\)")
                .matcher(sql);
        assertThat(m.find()).as("ATTENDANCE_STREAK 시드 행").isTrue();
        assertThat(m.group(1)).isEqualTo("10000");
        assertThat(m.group(2)).isEqualTo("0");
        assertThat(m.group(3)).isEqualTo("1");
        assertThat(sql.split("'" + RewardRule.ATTENDANCE_STREAK + "'", -1).length - 1)
                .as("같은 코드를 시드에 두 번 넣지 않는다 (code 는 UNIQUE)").isEqualTo(1);
    }

    @Test
    @DisplayName("마이그레이션은 규칙이 없을 때만 같은 값으로 넣는다 (관리자가 고친 값은 덮어쓰지 않는다)")
    void migrationInsertsOnlyWhenMissing() throws Exception {
        String sql = read("/sql/migration/2026-11-09_reward_rule_attendance_streak_seed.sql");

        assertThat(sql).contains("'ATTENDANCE_STREAK'", "10000, 0", "WHERE NOT EXISTS");
        assertThat(sql).doesNotContainIgnoringCase("UPDATE reward_rule");
    }

    @Test
    @DisplayName("보상은 연속 5일째부터다 (시드 설명과 서비스 상수가 같다)")
    void rewardStartsAtFifthDay() {
        assertThat(AttendanceService.REWARD_START_STREAK).isEqualTo(5);
    }
}
