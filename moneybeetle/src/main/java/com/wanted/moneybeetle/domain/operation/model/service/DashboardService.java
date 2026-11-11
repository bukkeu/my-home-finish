package com.wanted.moneybeetle.domain.operation.model.service;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.inquiry.model.service.InquiryService;
import com.wanted.moneybeetle.domain.member.model.dto.MemberCountDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.operation.model.dto.DashboardDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonSummaryDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TradingDashboardSummaryDTO;
import com.wanted.moneybeetle.domain.trading.model.service.SeasonAdminService;
import com.wanted.moneybeetle.domain.trading.model.service.TradingService;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   관리자 대시보드 (이슈 #33, API 84, ADM-08). 다른 도메인의 Service 조회 메서드만 모아서 보여 준다 (다른 도메인 Repository 직접 사용 금지).
*   - 검수 대기 강의 수 : course.CourseService.countReviewWaiting
*   - 답변 대기 문의 수 : inquiry.InquiryService.findInquiriesForAdmin 의 전체 개수. 사이트 문의와 강의 문의를 모두 세고(courseId 를 비움),
*     관리자가 숨기거나 지운 글은 뺀다. 한 건만 읽고 전체 개수만 쓴다 (전용 개수 메서드가 생기면 교체)
*   - 진행 중인 시즌·현재 턴·참가자 수 : trading.SeasonAdminService.findSeasons
*   - 오늘 주문 수·다음 턴 점검(nextTurnReady, missing) : trading.TradingService.getDashboardSummary (#193, 역할분담 §7)
*     그 요약은 진행·휴장 중인 시즌만 주므로, 정산 중(SETTLING)인 시즌이면 이 값들은 null 이다. 요약이 다른 시즌의 것이면(시즌 번호가 다르면) 쓰지 않고 null 로 둔다.
*   - 회원 수 : member.MemberService.countMembers
*   - 읽기 전용 트랜잭션 하나로 묶어서, 여러 도메인에서 가져온 숫자가 같은 시점의 값이 되게 한다 (각 서비스의 조회는 이 트랜잭션에 참여한다).
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    /** 답변 대기 문의 상태 값 (inquiry 의 문의 상태) */
    private static final String INQUIRY_WAITING = "WAITING";

    /** 진행 중으로 보는 시즌 상태. 준비(READY)와 종료(ENDED)는 뺀다 (trading 의 TradingSeason 상태 값) */
    private static final Set<String> ACTIVE_SEASON_STATUSES = Set.of("RUNNING", "HOLIDAY", "SETTLING");

    private final CourseService courseService;
    private final InquiryService inquiryService;
    private final SeasonAdminService seasonAdminService;
    private final MemberService memberService;
    private final TradingService tradingService;

    public DashboardDTO getDashboard() {
        long inquiryWaiting = inquiryService
                .findInquiriesForAdmin(null, INQUIRY_WAITING, false, false, PageRequest.of(0, 1))
                .getTotalElements();
        MemberCountDTO members = memberService.countMembers();

        return new DashboardDTO(
                courseService.countReviewWaiting(),
                inquiryWaiting,
                findActiveSeason(),
                new DashboardDTO.Members(members.total(), members.withdrawn()));
    }

    /** 시작일이 가장 늦은 진행 중 시즌 하나. 없으면 null */
    private DashboardDTO.Season findActiveSeason() {
        return seasonAdminService.findSeasons().stream()   // 시작일 최신순
                .filter(season -> ACTIVE_SEASON_STATUSES.contains(season.getStatus()))
                .findFirst()
                .map(this::toSeason)
                .orElse(null);
    }

    /**
     * 시즌 기본 정보에 trading 의 요약(오늘 주문 수·다음 턴 점검)을 붙인다. 요약은 진행·휴장 중인 시즌만 주고,
     * 정산 중이거나 다른 시즌의 요약이면 비어 있는 것으로 보고 그 값들을 null 로 둔다.
     */
    private DashboardDTO.Season toSeason(SeasonSummaryDTO season) {
        Optional<TradingDashboardSummaryDTO> summary = tradingService.getDashboardSummary()
                .filter(trading -> season.getSeasonId().equals(trading.seasonId()));
        return new DashboardDTO.Season(season.getSeasonId(), season.getCurrentTurn(), season.getParticipants(),
                summary.map(TradingDashboardSummaryDTO::todayOrders).orElse(null),
                summary.map(TradingDashboardSummaryDTO::nextTurnReady).orElse(null),
                summary.map(trading -> new DashboardDTO.Missing(trading.missing().prices(), trading.missing().news()))
                        .orElse(null));
    }
}
