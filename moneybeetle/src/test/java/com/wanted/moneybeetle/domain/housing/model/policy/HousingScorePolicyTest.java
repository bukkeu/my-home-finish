package com.wanted.moneybeetle.domain.housing.model.policy;

import com.wanted.moneybeetle.domain.housing.model.dto.ScoreDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/* comment.
*   담당 : SCM (모의 청약)
*   청약 가점 점수표 확인 (SP-71, 실제 가점제 별표1 기준)
*  */
class HousingScorePolicyTest {

    private final HousingScorePolicy policy = new HousingScorePolicy();

    @ParameterizedTest(name = "무주택 {0}년 → {1}점")
    @CsvSource({"0,2", "1,4", "3,8", "14,30", "15,32", "30,32"})
    void 무주택_기간은_1년_미만_2점_1년마다_2점_15년_이상_32점(int years, int expected) {
        assertThat(policy.homelessScore(years)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "부양가족 {0}명 → {1}점")
    @CsvSource({"0,5", "1,10", "3,20", "6,35", "9,35"})
    void 부양가족은_0명_5점_1명마다_5점_6명_이상_35점(int dependents, int expected) {
        assertThat(policy.dependentScore(dependents)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "납입 {0}회 → {1}점")
    @CsvSource({"0,1", "5,1", "6,2", "11,2", "12,3", "23,3", "24,4", "48,6", "179,16", "180,17", "500,17"})
    void 통장은_납입_1회를_1개월로_보고_가입기간_표를_적용한다(int paidCount, int expected) {
        assertThat(policy.accountScore(paidCount)).isEqualTo(expected);
    }

    @Test
    void 합계는_세_항목의_합이고_만점은_84() {
        ScoreDTO score = policy.calculate(3, 1, 48, LocalDate.of(2026, 11, 9));

        assertThat(score.getHomelessScore()).isEqualTo(8);
        assertThat(score.getDependentScore()).isEqualTo(10);
        assertThat(score.getAccountScore()).isEqualTo(6);
        assertThat(score.getTotalScore()).isEqualTo(24);      // API 명세서 68 예시와 같다
        assertThat(score.getMaxScore()).isEqualTo(84);
        assertThat(score.getBaseDate()).isEqualTo(LocalDate.of(2026, 11, 9));

        ScoreDTO max = policy.calculate(15, 6, 180, LocalDate.of(2026, 11, 9));
        assertThat(max.getTotalScore()).isEqualTo(84);
    }
}
