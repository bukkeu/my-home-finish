package com.wanted.moneybeetle.domain.growth.model.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.domain.growth.model.entity.RewardRule;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   시즌 랭킹 보상 규칙(RANKING_RANK_1~3) 시드가 확정값(1위 50,000 / 2위 30,000 / 3위 10,000캐시, 경험치 없음, 사용 중)으로 있는지 확인한다
*   (이슈 #10, SP-64 확정 #76). 시드는 DB 에 넣어야 돌아가서 테스트가 sample-data.sql 과 마이그레이션 파일의 값을 읽어 비교한다.
*   규칙이 없으면 랭킹 공개 때 보상 지급이 REWARD_RULE_NOT_FOUND 로 실패한다.
*  */
class RankingRewardSeedTest {

    private static final String[] CODES = {RewardRule.RANKING_RANK_1, RewardRule.RANKING_RANK_2, RewardRule.RANKING_RANK_3};
    private static final String[] CASH = {"50000", "30000", "10000"};

    private static String read(String path) throws Exception {
        try (InputStream in = RankingRewardSeedTest.class.getResourceAsStream(path)) {
            assertThat(in).as("클래스패스에 " + path + " 가 있어야 한다").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("sample-data.sql 에 랭킹 1·2·3위 규칙이 확정값(50,000 / 30,000 / 10,000, 경험치 0, 사용 중)으로 한 번씩 있다")
    void sampleDataHasRules() throws Exception {
        String sql = read("/sql/sample-data.sql");

        for (int i = 0; i < CODES.length; i++) {
            Matcher m = Pattern.compile("\\('" + CODES[i] + "',\\s*'[^']+',\\s*(\\d+),\\s*(\\d+),\\s*'[^']*',\\s*(\\d)\\)").matcher(sql);
            assertThat(m.find()).as(CODES[i] + " 시드 행").isTrue();
            assertThat(m.group(1)).isEqualTo(CASH[i]);
            assertThat(m.group(2)).isEqualTo("0");
            assertThat(m.group(3)).isEqualTo("1");
            assertThat(sql.split("'" + CODES[i] + "'", -1).length - 1)
                    .as("같은 코드를 시드에 두 번 넣지 않는다 (code 는 UNIQUE)").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("마이그레이션은 규칙이 없을 때만 같은 값으로 넣고 (관리자가 고친 값은 덮어쓰지 않음), 캐시 내역 원인 종류 설명에 RANKING 을 더한다")
    void migrationInsertsOnlyWhenMissing() throws Exception {
        String sql = read("/sql/migration/2026-11-10_reward_rule_ranking_seed.sql");

        for (int i = 0; i < CODES.length; i++) {
            assertThat(sql).contains("'" + CODES[i] + "'", CASH[i] + ", 0", "WHERE code = '" + CODES[i] + "'");
        }
        assertThat(sql).contains("WHERE NOT EXISTS").contains("RANKING");
        assertThat(sql).doesNotContainIgnoringCase("UPDATE reward_rule");
    }

    @Test
    @DisplayName("순위(1~3)와 규칙 코드의 짝이 맞고, 그 밖의 순위는 거절한다")
    void rankToRuleCode() {
        assertThat(RewardRule.rankingRuleCode(1)).isEqualTo(RewardRule.RANKING_RANK_1);
        assertThat(RewardRule.rankingRuleCode(2)).isEqualTo(RewardRule.RANKING_RANK_2);
        assertThat(RewardRule.rankingRuleCode(3)).isEqualTo(RewardRule.RANKING_RANK_3);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> RewardRule.rankingRuleCode(4))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
