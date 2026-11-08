package com.wanted.moneybeetle.domain.growth.model.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.domain.growth.model.entity.LevelPolicy;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   sample-data.sql 의 level_policy 시드가 이슈 #11 확정값(LevelTableFixture)과 같은지 확인한다.
*   시드는 DB 에 넣어야 돌아가서 테스트가 직접 실행하지 못하므로, 파일의 INSERT 값을 읽어 비교한다.
*  */
class LevelSeedTest {

    /** 클래스패스 위치다 (src/main/resources/sql/sample-data.sql). 작업 디렉터리에 상관없이 읽힌다 */
    private static final String SEED = "/sql/sample-data.sql";
    private static final Pattern ROW = Pattern.compile("\\((\\d+),\\s*(\\d+),\\s*'([^']+)',\\s*(\\d+)\\)");

    @Test
    @DisplayName("sample-data.sql 의 level_policy 20줄이 확정값과 같고 정책표 검증을 통과한다")
    void seedMatchesConfirmedValues() throws Exception {
        String sql;
        try (InputStream in = LevelSeedTest.class.getResourceAsStream(SEED)) {
            assertThat(in).as("클래스패스에 " + SEED + " 가 있어야 한다").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int start = sql.indexOf("INSERT INTO level_policy");
        assertThat(start).as("level_policy INSERT 가 있어야 한다").isGreaterThanOrEqualTo(0);
        String statement = sql.substring(start, sql.indexOf(';', start));

        List<LevelPolicy> seeded = new ArrayList<>();
        Matcher m = ROW.matcher(statement);
        while (m.find()) {
            seeded.add(LevelPolicy.create(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    m.group(3), Integer.parseInt(m.group(4))));
        }

        List<LevelPolicy> expected = LevelTableFixture.policies();
        assertThat(seeded).hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertThat(seeded.get(i)).usingRecursiveComparison().ignoringFields("levelPolicyId")
                    .isEqualTo(expected.get(i));
        }
        new LevelTable(seeded); // 1레벨부터 빠짐없이, 필요 경험치가 계속 늘어나는지
    }
}
