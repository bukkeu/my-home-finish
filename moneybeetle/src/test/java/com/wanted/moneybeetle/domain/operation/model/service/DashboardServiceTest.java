package com.wanted.moneybeetle.domain.operation.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.inquiry.model.dto.AdminInquiryListItemDTO;
import com.wanted.moneybeetle.domain.inquiry.model.service.InquiryService;
import com.wanted.moneybeetle.domain.member.model.dto.MemberCountDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonSummaryDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TradingDashboardSummaryDTO;
import com.wanted.moneybeetle.domain.trading.model.service.SeasonAdminService;
import com.wanted.moneybeetle.domain.trading.model.service.TradingService;
import com.wanted.moneybeetle.global.common.PageResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class DashboardServiceTest {

    private CourseService courseService;
    private InquiryService inquiryService;
    private SeasonAdminService seasonAdminService;
    private MemberService memberService;
    private TradingService tradingService;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        courseService = mock(CourseService.class);
        inquiryService = mock(InquiryService.class);
        seasonAdminService = mock(SeasonAdminService.class);
        memberService = mock(MemberService.class);
        tradingService = mock(TradingService.class);
        service = new DashboardService(courseService, inquiryService, seasonAdminService, memberService, tradingService);

        when(courseService.countReviewWaiting()).thenReturn(3L);
        givenInquiryWaiting(2);
        when(memberService.countMembers()).thenReturn(new MemberCountDTO(5, 1));
        when(seasonAdminService.findSeasons()).thenReturn(List.of());
        when(tradingService.getDashboardSummary()).thenReturn(Optional.empty());
    }

    private void givenInquiryWaiting(long total) {
        PageResponse<AdminInquiryListItemDTO> page = PageResponse.from(
                new PageImpl<AdminInquiryListItemDTO>(List.of(), PageRequest.of(0, 1), total));
        when(inquiryService.findInquiriesForAdmin(any(), eq("WAITING"), any(), any(), any(Pageable.class)))
                .thenReturn(page);
    }

    private SeasonSummaryDTO season(long id, String status, int turn, long participants, int daysAgo) {
        return new SeasonSummaryDTO(id, "시즌 " + id, LocalDate.of(2026, 11, 9).minusDays(daysAgo), status, turn,
                participants);
    }

    @Test
    @DisplayName("검수 대기 강의·답변 대기 문의·회원 수를 각 도메인 서비스에서 모은다")
    void collectsCounts() {
        var dashboard = service.getDashboard();

        assertThat(dashboard.reviewWaiting()).isEqualTo(3);
        assertThat(dashboard.inquiryWaiting()).isEqualTo(2);
        assertThat(dashboard.members().total()).isEqualTo(5);
        assertThat(dashboard.members().withdrawn()).isEqualTo(1);
    }

    @Test
    @DisplayName("답변 대기 문의는 강의를 가리지 않고(사이트·강의 문의 모두), 숨김·삭제를 빼고, 한 건만 읽어 전체 개수를 쓴다")
    void inquiryWaitingUsesTotalOnly() {
        givenInquiryWaiting(42);

        assertThat(service.getDashboard().inquiryWaiting()).isEqualTo(42);
        verify(inquiryService).findInquiriesForAdmin(eq(null), eq("WAITING"), eq(false), eq(false), eq(PageRequest.of(0, 1)));
    }

    @Test
    @DisplayName("진행 중인 시즌이 없으면 season 은 null 이다 (준비·종료만 있어도)")
    void noActiveSeason() {
        when(seasonAdminService.findSeasons()).thenReturn(List.of(
                season(8, "READY", 0, 0, 0), season(6, "ENDED", 12, 900, 30)));

        assertThat(service.getDashboard().season()).isNull();
    }

    @Test
    @DisplayName("진행 중인 시즌 중 가장 최근 시즌의 번호·현재 턴·참가자 수와, trading 요약의 오늘 주문 수·다음 턴 점검 결과를 담는다")
    void activeSeason() {
        when(seasonAdminService.findSeasons()).thenReturn(List.of(   // 시작일 최신순
                season(8, "READY", 0, 0, 0),
                season(7, "RUNNING", 5, 1240, 4),
                season(5, "SETTLING", 12, 800, 40)));
        when(tradingService.getDashboardSummary()).thenReturn(Optional.of(
                new TradingDashboardSummaryDTO(7L, 3820, false, new TradingDashboardSummaryDTO.Missing(2, 0))));

        var season = service.getDashboard().season();

        assertThat(season.seasonId()).isEqualTo(7L);
        assertThat(season.currentTurn()).isEqualTo(5);
        assertThat(season.participants()).isEqualTo(1240);
        assertThat(season.todayOrders()).isEqualTo(3820L);
        assertThat(season.nextTurnReady()).isFalse();
        assertThat(season.missing().prices()).isEqualTo(2);
        assertThat(season.missing().news()).isZero();
    }

    @Test
    @DisplayName("다음 턴 준비가 끝났으면 nextTurnReady 는 true 이고 누락은 0 이다 (뉴스가 없으면 news 1)")
    void nextTurnReadyAndNewsMissing() {
        when(seasonAdminService.findSeasons()).thenReturn(List.of(season(7, "RUNNING", 5, 10, 1)));
        when(tradingService.getDashboardSummary()).thenReturn(Optional.of(
                new TradingDashboardSummaryDTO(7L, 0, true, new TradingDashboardSummaryDTO.Missing(0, 0))));
        var ready = service.getDashboard().season();
        assertThat(ready.todayOrders()).isZero();   // 오늘 주문이 없어도 값은 0 이고 null 이 아니다
        assertThat(ready.nextTurnReady()).isTrue();
        assertThat(ready.missing().prices()).isZero();

        when(tradingService.getDashboardSummary()).thenReturn(Optional.of(
                new TradingDashboardSummaryDTO(7L, 5, false, new TradingDashboardSummaryDTO.Missing(0, 1))));
        assertThat(service.getDashboard().season().missing().news()).isEqualTo(1);
    }

    @Test
    @DisplayName("정산 중인 시즌은 trading 요약이 없으므로 시즌 정보는 담고 주문 수·다음 턴 점검은 null 이다")
    void settlingSeasonHasNoTradingSummary() {
        when(seasonAdminService.findSeasons()).thenReturn(List.of(season(7, "SETTLING", 12, 10, 1)));
        when(tradingService.getDashboardSummary()).thenReturn(Optional.empty());

        var season = service.getDashboard().season();

        assertThat(season.seasonId()).isEqualTo(7L);
        assertThat(season.todayOrders()).isNull();
        assertThat(season.nextTurnReady()).isNull();
        assertThat(season.missing()).isNull();
    }

    @Test
    @DisplayName("trading 요약이 다른 시즌의 것이면(시즌 번호가 다르면) 쓰지 않고 null 로 둔다")
    void summaryOfAnotherSeasonIsIgnored() {
        when(seasonAdminService.findSeasons()).thenReturn(List.of(season(8, "SETTLING", 12, 10, 1)));
        when(tradingService.getDashboardSummary()).thenReturn(Optional.of(
                new TradingDashboardSummaryDTO(7L, 99, true, new TradingDashboardSummaryDTO.Missing(0, 0))));

        var season = service.getDashboard().season();

        assertThat(season.seasonId()).isEqualTo(8L);
        assertThat(season.todayOrders()).isNull();
        assertThat(season.nextTurnReady()).isNull();
        assertThat(season.missing()).isNull();
    }

    @Test
    @DisplayName("진행 중인 시즌이 없으면 trading 요약은 부르지 않는다")
    void noActiveSeasonSkipsTradingSummary() {
        when(seasonAdminService.findSeasons()).thenReturn(List.of(season(6, "ENDED", 12, 900, 30)));

        assertThat(service.getDashboard().season()).isNull();
        verify(tradingService, never()).getDashboardSummary();
    }

    @Test
    @DisplayName("휴장·정산 중인 시즌도 진행 중으로 본다")
    void holidayAndSettlingCountAsActive() {
        when(seasonAdminService.findSeasons()).thenReturn(List.of(season(7, "HOLIDAY", 5, 10, 1)));
        assertThat(service.getDashboard().season().seasonId()).isEqualTo(7L);

        when(seasonAdminService.findSeasons()).thenReturn(List.of(season(7, "SETTLING", 12, 10, 1)));
        assertThat(service.getDashboard().season().seasonId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("대시보드는 읽기 전용 트랜잭션 하나로 묶여 여러 도메인의 숫자가 같은 시점의 값이다")
    void isOneReadOnlyTransaction() {
        var tx = DashboardService.class.getAnnotation(org.springframework.transaction.annotation.Transactional.class);

        org.assertj.core.api.Assertions.assertThat(tx).as("클래스에 @Transactional 이 있어야 한다").isNotNull();
        org.assertj.core.api.Assertions.assertThat(tx.readOnly()).isTrue();
    }
}
