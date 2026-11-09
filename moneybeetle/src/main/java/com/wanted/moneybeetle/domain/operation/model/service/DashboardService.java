package com.wanted.moneybeetle.domain.operation.model.service;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.inquiry.model.service.InquiryService;
import com.wanted.moneybeetle.domain.member.model.dto.MemberCountDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.operation.model.dto.DashboardDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonSummaryDTO;
import com.wanted.moneybeetle.domain.trading.model.service.SeasonAdminService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   관리자 대시보드 (이슈 #33, API 84, ADM-08). 다른 도메인의 Service 조회 메서드만 모아서 보여 준다 (다른 도메인 Repository 직접 사용 금지).
*   - 검수 대기 강의 수 : course.CourseService.countReviewWaiting
*   - 답변 대기 문의 수 : inquiry.InquiryService.findInquiries 의 전체 개수 (전용 개수 메서드가 생기면 교체)
*   - 진행 중인 시즌·현재 턴·참가자 수 : trading.SeasonAdminService.findSeasons
*   - 회원 수 : member.MemberService.countMembers
*   - 오늘 주문 수·다음 턴 점검(nextTurnReady, missing)은 trading 에 조회 메서드가 아직 없어 null 로 둔다.
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

    public DashboardDTO getDashboard(Long adminId) {
        long inquiryWaiting = inquiryService
                .findInquiries(adminId, true, null, INQUIRY_WAITING, PageRequest.of(0, 1))
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
                .map(DashboardService::toSeason)
                .orElse(null);
    }

    private static DashboardDTO.Season toSeason(SeasonSummaryDTO season) {
        return new DashboardDTO.Season(season.getSeasonId(), season.getCurrentTurn(), season.getParticipants(),
                null, null, null);
    }
}
