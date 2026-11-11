package com.wanted.moneybeetle.domain.housing.model.policy;

import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/* comment.
*   담당 : SCM (모의 청약)
*   HousingWinnerPolicy #48 확인 (SP-75)
*   - 가점 높은 순 → 납입회차 많은 순 → 추첨, 공급 세대 수만큼 WIN
*   - 신청자가 세대 수보다 적으면 모두 WIN
*  */
class HousingWinnerPolicyTest {

    private final HousingWinnerPolicy policy = new HousingWinnerPolicy(new Random(42));

    // 가점 = 무주택 점수에 몰아서 넣는다 (부양가족·통장 0)
    private HousingApplication app(long memberId, int totalScore, int paidCount) {
        return HousingApplication.create(memberId, 1L, "MB-261105-" + memberId, 0, 0, paidCount,
                totalScore, 0, 0, LocalDateTime.of(2026, 11, 5, 9, 0));
    }

    @Test
    void 가점이_높은_순서로_세대_수만큼_당첨() {
        HousingApplication low = app(1, 20, 10);
        HousingApplication high = app(2, 60, 10);
        HousingApplication middle = app(3, 40, 10);

        int winners = policy.rank(List.of(low, high, middle), 2);

        assertThat(winners).isEqualTo(2);
        assertThat(high.getResultRank()).isEqualTo(1);
        assertThat(middle.getResultRank()).isEqualTo(2);
        assertThat(low.getResultRank()).isEqualTo(3);
        assertThat(high.isWin()).isTrue();
        assertThat(middle.isWin()).isTrue();
        assertThat(low.getResult()).isEqualTo(HousingApplication.RESULT_LOSE);
    }

    @Test
    void 가점이_같으면_납입회차가_많은_사람이_앞선다() {
        HousingApplication fewPaid = app(1, 40, 12);
        HousingApplication manyPaid = app(2, 40, 48);

        policy.rank(List.of(fewPaid, manyPaid), 1);

        assertThat(manyPaid.isWin()).isTrue();
        assertThat(fewPaid.isWin()).isFalse();
    }

    @Test
    void 가점과_납입회차가_모두_같으면_추첨이라_누구든_당첨될_수_있다() {
        // 서버처럼 하나의 Random 으로 100번 추첨해 보면 두 사람 모두 한 번 이상 당첨된다
        HousingWinnerPolicy lottery = new HousingWinnerPolicy(new Random(7));
        Set<Long> winnersSeen = IntStream.range(0, 100).mapToObj(i -> {
            HousingApplication a = app(1, 40, 24);
            HousingApplication b = app(2, 40, 24);
            lottery.rank(List.of(a, b), 1);
            return a.isWin() ? a.getMemberId() : b.getMemberId();
        }).collect(Collectors.toSet());

        assertThat(winnersSeen).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void 신청자가_세대_수보다_적으면_모두_당첨() {
        List<HousingApplication> applications = new ArrayList<>(List.of(app(1, 10, 1), app(2, 20, 2)));

        int winners = policy.rank(applications, 120);

        assertThat(winners).isEqualTo(2);
        assertThat(applications).allMatch(HousingApplication::isWin);
    }

    @Test
    void 신청자가_없으면_당첨자도_없다() {
        assertThat(policy.rank(List.of(), 3)).isZero();
    }
}
