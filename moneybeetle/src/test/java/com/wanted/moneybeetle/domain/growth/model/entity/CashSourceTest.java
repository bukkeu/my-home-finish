package com.wanted.moneybeetle.domain.growth.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   CashSource (이슈 #287) - 서버·DB 없이 돈다
*  */
class CashSourceTest {

    @Test
    @DisplayName("원인 종류 7개가 있고, 화면 문구(label)는 예전 CashHistoryDTO 의 문구와 같다")
    void labels() {
        assertThat(CashSource.values()).hasSize(7);
        assertThat(CashSource.ATTENDANCE.getLabel()).isEqualTo("연속 출석 보상");
        assertThat(CashSource.COURSE_COMPLETE.getLabel()).isEqualTo("강의 완료");
        assertThat(CashSource.LEVEL_UP.getLabel()).isEqualTo("레벨업 보상");
        assertThat(CashSource.SEASON_JOIN.getLabel()).isEqualTo("모의투자 시즌 참가");
        assertThat(CashSource.SUBSCRIPTION_DEPOSIT.getLabel()).isEqualTo("청약통장 납입");
        assertThat(CashSource.ADMIN.getLabel()).isEqualTo("관리자 조정");
        assertThat(CashSource.RANKING.getLabel()).isEqualTo("시즌 랭킹 보상");
    }
}
