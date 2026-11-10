package com.wanted.moneybeetle.domain.housing.model.policy;

import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/* comment.
*   담당 : SCM (모의 청약)
*   당첨자 산정 규칙 (SP-75, #48)
*   - 순위 : 가점 높은 순 → 같으면 신청 시점 납입회차 많은 순 → 그래도 같으면 추첨
*   - 당첨 : 1위부터 공급 세대 수만큼 WIN, 나머지 LOSE (신청자가 세대 수보다 적으면 모두 WIN)
*   추첨 방법 : 먼저 무작위로 섞은 뒤 가점·납입회차로 정렬한다.
*              정렬은 같은 값끼리 순서를 바꾸지 않으므로(안정 정렬) 동점자끼리만 무작위 순서가 남는다.
*   산정 결과(순위)는 DB 에 저장되므로 무작위여도 한 번 정해지면 바뀌지 않는다
*  */
@Component
public class HousingWinnerPolicy {

    private static final Comparator<HousingApplication> BY_SCORE_THEN_PAID_COUNT =
            Comparator.comparingInt(HousingApplication::getTotalScore).reversed()
                    .thenComparing(Comparator.comparingInt(HousingApplication::getPaidCountSnapshot).reversed());

    private final Random random;

    // 서버에서는 진짜 무작위
    public HousingWinnerPolicy() {
        this(new Random());
    }

    // 테스트에서는 씨앗값을 고정한 Random 을 넣어 결과를 재현한다
    public HousingWinnerPolicy(Random random) {
        this.random = random;
    }

    // 순위를 매기고 결과를 기록한다. 돌려주는 값 = 당첨자 수
    public int rank(List<HousingApplication> applications, int supplyUnits) {
        List<HousingApplication> ordered = new ArrayList<>(applications);
        Collections.shuffle(ordered, random);      // ① 추첨 : 무작위로 섞기
        ordered.sort(BY_SCORE_THEN_PAID_COUNT);     // ② 가점 → 납입회차 (동점자는 ①의 순서 유지)

        int winners = 0;
        for (int i = 0; i < ordered.size(); i++) {
            boolean win = i < supplyUnits;
            ordered.get(i).changeResult(i + 1, win);
            if (win) {
                winners++;
            }
        }
        return winners;
    }
}
