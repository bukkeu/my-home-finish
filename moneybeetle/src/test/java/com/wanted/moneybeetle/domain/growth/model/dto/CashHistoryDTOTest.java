package com.wanted.moneybeetle.domain.growth.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.domain.growth.model.entity.CashHistory;
import com.wanted.moneybeetle.domain.growth.model.entity.CashSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   CashHistoryDTO 의 제목(title) (이슈 #287) - 원인 종류별 문구가 switch 없이 CashSource.label 로 나와도 예전과 같은지 확인한다
*  */
class CashHistoryDTOTest {

    @Test
    @DisplayName("원인 종류별 제목은 예전 문구와 같다")
    void titleBySource() {
        assertThat(titleOfEarn(CashSource.ATTENDANCE)).isEqualTo("연속 출석 보상");
        assertThat(titleOfEarn(CashSource.COURSE_COMPLETE)).isEqualTo("강의 완료");
        assertThat(titleOfEarn(CashSource.LEVEL_UP)).isEqualTo("레벨업 보상");
        assertThat(titleOfEarn(CashSource.RANKING)).isEqualTo("시즌 랭킹 보상");
        assertThat(CashHistoryDTO.from(CashHistory.use(1L, CashSource.SEASON_JOIN, 5L, 1_000, 0)).getTitle())
                .isEqualTo("모의투자 시즌 참가");
        assertThat(CashHistoryDTO.from(CashHistory.use(1L, CashSource.SUBSCRIPTION_DEPOSIT, 5L, 1_000, 0)).getTitle())
                .isEqualTo("청약통장 납입");
    }

    @Test
    @DisplayName("관리자 지급·회수는 \"관리자 조정 · 사유\" 로 보인다")
    void adminTitleHasReason() {
        assertThat(CashHistoryDTO.from(CashHistory.adminGive(1L, 9L, 2_000, 2_000, "출석 누락 보정")).getTitle())
                .isEqualTo("관리자 조정 · 출석 누락 보정");
        assertThat(CashHistoryDTO.from(CashHistory.adminTake(1L, 9L, 500, 1_500, "오지급 회수")).getTitle())
                .isEqualTo("관리자 조정 · 오지급 회수");
    }

    @Test
    @DisplayName("관리자 조정인데 사유가 비어 있는 옛 데이터는 \"관리자 조정\" 만 보인다")
    void adminTitleWithoutReason() {
        CashHistory withoutReason = CashHistory.adminGive(1L, 9L, 2_000, 2_000, "임시");
        org.springframework.test.util.ReflectionTestUtils.setField(withoutReason, "reason", null);

        assertThat(CashHistoryDTO.from(withoutReason).getTitle()).isEqualTo("관리자 조정");
    }

    private static String titleOfEarn(CashSource source) {
        return CashHistoryDTO.from(CashHistory.earn(1L, source, 7L, null, 3_000, 3_000)).getTitle();
    }
}
