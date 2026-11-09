package com.wanted.moneybeetle.domain.operation.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryListItemDTO;
import com.wanted.moneybeetle.domain.inquiry.model.service.InquiryService;
import com.wanted.moneybeetle.domain.member.model.dto.MemberCountDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonSummaryDTO;
import com.wanted.moneybeetle.domain.trading.model.service.SeasonAdminService;
import com.wanted.moneybeetle.global.common.PageResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class DashboardServiceTest {

    private static final Long ADMIN = 1L;

    private CourseService courseService;
    private InquiryService inquiryService;
    private SeasonAdminService seasonAdminService;
    private MemberService memberService;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        courseService = mock(CourseService.class);
        inquiryService = mock(InquiryService.class);
        seasonAdminService = mock(SeasonAdminService.class);
        memberService = mock(MemberService.class);
        service = new DashboardService(courseService, inquiryService, seasonAdminService, memberService);

        when(courseService.countReviewWaiting()).thenReturn(3L);
        givenInquiryWaiting(2);
        when(memberService.countMembers()).thenReturn(new MemberCountDTO(5, 1));
        when(seasonAdminService.findSeasons()).thenReturn(List.of());
    }

    private void givenInquiryWaiting(long total) {
        PageResponse<InquiryListItemDTO> page = PageResponse.from(
                new PageImpl<InquiryListItemDTO>(List.of(), PageRequest.of(0, 1), total));
        when(inquiryService.findInquiries(eq(ADMIN), eq(true), any(), eq("WAITING"), any(Pageable.class)))
                .thenReturn(page);
    }

    private SeasonSummaryDTO season(long id, String status, int turn, long participants, int daysAgo) {
        return new SeasonSummaryDTO(id, "시즌 " + id, LocalDate.of(2026, 11, 9).minusDays(daysAgo), status, turn,
                participants);
    }

    @Test
    @DisplayName("검수 대기 강의·답변 대기 문의·회원 수를 각 도메인 서비스에서 모은다")
    void collectsCounts() {
        var dashboard = service.getDashboard(ADMIN);

        assertThat(dashboard.reviewWaiting()).isEqualTo(3);
        assertThat(dashboard.inquiryWaiting()).isEqualTo(2);
        assertThat(dashboard.members().total()).isEqualTo(5);
        assertThat(dashboard.members().withdrawn()).isEqualTo(1);
    }

    @Test
    @DisplayName("답변 대기 문의는 한 건만 읽고 전체 개수를 쓴다 (관리자 권한으로 조회)")
    void inquiryWaitingUsesTotalOnly() {
        givenInquiryWaiting(42);

        assertThat(service.getDashboard(ADMIN).inquiryWaiting()).isEqualTo(42);
        verify(inquiryService).findInquiries(eq(ADMIN), eq(true), any(), eq("WAITING"), eq(PageRequest.of(0, 1)));
    }

    @Test
    @DisplayName("진행 중인 시즌이 없으면 season 은 null 이다 (준비·종료만 있어도)")
    void noActiveSeason() {
        when(seasonAdminService.findSeasons()).thenReturn(List.of(
                season(8, "READY", 0, 0, 0), season(6, "ENDED", 12, 900, 30)));

        assertThat(service.getDashboard(ADMIN).season()).isNull();
    }

    @Test
    @DisplayName("진행 중인 시즌 중 가장 최근 시즌의 번호·현재 턴·참가자 수를 담고, trading 이 못 주는 값은 null 이다")
    void activeSeason() {
        when(seasonAdminService.findSeasons()).thenReturn(List.of(   // 시작일 최신순
                season(8, "READY", 0, 0, 0),
                season(7, "RUNNING", 5, 1240, 4),
                season(5, "SETTLING", 12, 800, 40)));

        var season = service.getDashboard(ADMIN).season();

        assertThat(season.seasonId()).isEqualTo(7L);
        assertThat(season.currentTurn()).isEqualTo(5);
        assertThat(season.participants()).isEqualTo(1240);
        assertThat(season.todayOrders()).isNull();
        assertThat(season.nextTurnReady()).isNull();
        assertThat(season.missing()).isNull();
    }

    @Test
    @DisplayName("휴장·정산 중인 시즌도 진행 중으로 본다")
    void holidayAndSettlingCountAsActive() {
        when(seasonAdminService.findSeasons()).thenReturn(List.of(season(7, "HOLIDAY", 5, 10, 1)));
        assertThat(service.getDashboard(ADMIN).season().seasonId()).isEqualTo(7L);

        when(seasonAdminService.findSeasons()).thenReturn(List.of(season(7, "SETTLING", 12, 10, 1)));
        assertThat(service.getDashboard(ADMIN).season().seasonId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("대시보드는 읽기 전용 트랜잭션 하나로 묶여 여러 도메인의 숫자가 같은 시점의 값이다")
    void isOneReadOnlyTransaction() {
        var tx = DashboardService.class.getAnnotation(org.springframework.transaction.annotation.Transactional.class);

        org.assertj.core.api.Assertions.assertThat(tx).as("클래스에 @Transactional 이 있어야 한다").isNotNull();
        org.assertj.core.api.Assertions.assertThat(tx.readOnly()).isTrue();
    }
}
