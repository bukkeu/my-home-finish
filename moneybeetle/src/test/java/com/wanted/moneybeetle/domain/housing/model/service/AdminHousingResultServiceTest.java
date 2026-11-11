package com.wanted.moneybeetle.domain.housing.model.service;

import com.wanted.moneybeetle.domain.housing.exception.HousingErrorCode;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingApplicationRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingComplexRepository;
import com.wanted.moneybeetle.domain.housing.model.dto.AnnounceResultDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ApplicantDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexRequestDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComputeResultDTO;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import com.wanted.moneybeetle.domain.housing.model.policy.HousingWinnerPolicy;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.notification.model.entity.Notification;
import com.wanted.moneybeetle.domain.notification.model.service.NotificationService;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.global.config.TimeConfig;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* comment.
*   담당 : SCM (모의 청약)
*   AdminHousingService #48 확인 - 신청자 목록·당첨자 산정·결과 발표 (SP-75)
*   단지 : 신청 11/3 ~ 11/10, 발표 11/17, 2세대
*   - 산정 : 마감 다음 날(11/11)부터, 한 번만, 단지 행 잠금 조회 사용
*   - 산정한 공고는 일정·세대 수를 바꿀 수 없다 (신청이 다시 열리지 않게, 정보 수정은 가능)
*   - 발표 : 산정 뒤 발표일(11/17)부터, 한 번만, 신청자 모두에게 알림
*  */
class AdminHousingResultServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final LocalDate CLOSED_DAY = LocalDate.of(2026, 11, 11);
    private static final LocalDate ANNOUNCE_DAY = LocalDate.of(2026, 11, 17);

    private HousingComplexRepository complexRepository;
    private HousingApplicationRepository applicationRepository;
    private MemberService memberService;
    private NotificationService notificationService;
    private final AdminLogService adminLogService = mock(AdminLogService.class);
    private HousingComplex complex;

    @BeforeEach
    void setUp() {
        complexRepository = mock(HousingComplexRepository.class);
        applicationRepository = mock(HousingApplicationRepository.class);
        memberService = mock(MemberService.class);
        notificationService = mock(NotificationService.class);

        complex = HousingComplex.create("비틀 파크", "서울 강서구", "84㎡", 650_000_000L, 2, "일반공급",
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3),
                LocalDate.of(2026, 11, 10), ANNOUNCE_DAY, 1L);
        ReflectionTestUtils.setField(complex, "id", 1L);
        when(complexRepository.findForUpdate(1L)).thenReturn(Optional.of(complex));
        when(complexRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(complex));
    }

    private AdminHousingService serviceOn(LocalDate today) {
        Clock fixed = Clock.fixed(today.atStartOfDay(TimeConfig.SEOUL).toInstant(), TimeConfig.SEOUL);
        return new AdminHousingService(complexRepository, applicationRepository, fixed,
                new HousingWinnerPolicy(new Random(1)), memberService, notificationService, adminLogService);
    }

    private HousingApplication app(long id, long memberId, int totalScore, int paidCount) {
        HousingApplication app = HousingApplication.create(memberId, 1L, "MB-261105-000" + id, 0, 0, paidCount,
                totalScore, 0, 0, LocalDateTime.of(2026, 11, 5, 9, (int) id));
        ReflectionTestUtils.setField(app, "id", id);
        return app;
    }

    private void assertRejected(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(code);
    }

    // ── API 146 신청자 목록 ──

    @Test
    void 신청자_목록은_닉네임과_함께_가점_높은_순() {
        when(applicationRepository.findByHousingComplexId(1L))
                .thenReturn(List.of(app(1, 7, 20, 10), app(2, 8, 50, 10)));
        when(memberService.findNicknames(any())).thenReturn(Map.of(7L, "민지", 8L, "준호"));

        List<ApplicantDTO> result = serviceOn(CLOSED_DAY).findApplicants(1L);

        assertThat(result).extracting(ApplicantDTO::getNickname).containsExactly("준호", "민지");
        assertThat(result.get(0).getResult()).isNull();
    }

    // ── API 147 당첨자 산정 ──

    @Test
    void 마감_다음_날_산정하면_세대_수만큼_당첨되고_산정_일시가_남는다() {
        HousingApplication first = app(1, 7, 60, 10);
        HousingApplication second = app(2, 8, 40, 10);
        HousingApplication third = app(3, 9, 20, 10);
        when(applicationRepository.findByHousingComplexId(1L)).thenReturn(List.of(third, first, second));

        ComputeResultDTO result = serviceOn(CLOSED_DAY).compute(ADMIN_ID, 1L);

        assertThat(result.getApplicants()).isEqualTo(3);
        assertThat(result.getWinners()).isEqualTo(2);
        assertThat(complex.isComputed()).isTrue();
        assertThat(first.isWin()).isTrue();
        assertThat(second.isWin()).isTrue();
        assertThat(third.getResult()).isEqualTo(HousingApplication.RESULT_LOSE);
        assertThat(complex.isAnnounced()).isFalse();      // 산정만으로는 회원에게 보이지 않는다
        verify(complexRepository).findForUpdate(1L);      // 단지 행을 잠그고 산정
    }

    @Test
    void 신청_마감일까지는_산정할_수_없다() {
        assertRejected(() -> serviceOn(LocalDate.of(2026, 11, 10)).compute(ADMIN_ID, 1L), HousingErrorCode.APPLY_NOT_CLOSED);
        assertThat(complex.isComputed()).isFalse();
    }

    @Test
    void 산정한_공고는_신청자가_없어도_일정을_바꿔_신청을_다시_열_수_없다() {
        complex.markComputed(LocalDateTime.of(2026, 11, 11, 9, 0));
        when(applicationRepository.countByHousingComplexId(1L)).thenReturn(0L);
        ComplexRequestDTO reopen = new ComplexRequestDTO("비틀 파크", "서울 강서구", "84㎡", 650_000_000L, 2, "일반공급",
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3),
                LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 7));   // 마감·발표를 미래로

        assertRejected(() -> serviceOn(CLOSED_DAY).updateComplex(ADMIN_ID, 1L, reopen), HousingErrorCode.ALREADY_COMPUTED);
        assertThat(complex.getApplyEndDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void 산정한_공고도_정보만_바꾸는_수정은_된다() {
        complex.markComputed(LocalDateTime.of(2026, 11, 11, 9, 0));
        ComplexRequestDTO rename = new ComplexRequestDTO("비틀 파크 2차", "서울 강서구", "84㎡", 650_000_000L, 2,
                "일반공급", LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3),
                LocalDate.of(2026, 11, 10), ANNOUNCE_DAY);

        serviceOn(CLOSED_DAY).updateComplex(ADMIN_ID, 1L, rename);

        assertThat(complex.getName()).isEqualTo("비틀 파크 2차");
    }

    @Test
    void 이미_산정한_공고는_다시_산정할_수_없다() {
        complex.markComputed(LocalDateTime.of(2026, 11, 11, 9, 0));

        assertRejected(() -> serviceOn(CLOSED_DAY).compute(ADMIN_ID, 1L), HousingErrorCode.ALREADY_COMPUTED);
        verify(applicationRepository, never()).findByHousingComplexId(any());
    }

    @Test
    void 신청자가_없어도_산정은_완료로_기록한다() {
        when(applicationRepository.findByHousingComplexId(1L)).thenReturn(List.of());

        ComputeResultDTO result = serviceOn(CLOSED_DAY).compute(ADMIN_ID, 1L);

        assertThat(result.getWinners()).isZero();
        assertThat(complex.isComputed()).isTrue();
    }

    // ── API 148 결과 발표 ──

    @Test
    void 발표일에_발표하면_상태가_ANNOUNCED_가_되고_신청자_모두에게_알림() {
        complex.markComputed(LocalDateTime.of(2026, 11, 11, 9, 0));
        when(applicationRepository.findByHousingComplexId(1L))
                .thenReturn(List.of(app(1, 7, 60, 10), app(2, 8, 40, 10)));

        AnnounceResultDTO result = serviceOn(ANNOUNCE_DAY).announce(ADMIN_ID, 1L);

        assertThat(complex.isAnnounced()).isTrue();
        assertThat(complex.getAnnouncedAt()).isEqualTo(ANNOUNCE_DAY.atStartOfDay());
        assertThat(result.getNotified()).isEqualTo(2);
        verify(notificationService).send(eq(7L), eq("HOUSING_RESULT"), anyString(), eq("/user/demo-housing-subscription"));
        verify(notificationService).send(eq(8L), eq("HOUSING_RESULT"), anyString(), eq("/user/demo-housing-subscription"));
    }

    @Test
    void 알림_제목은_단지_이름을_넣고_100자를_넘으면_이름만_줄인다() {
        assertThat(AdminHousingService.resultTitle("비틀 파크")).isEqualTo("비틀 파크 청약 결과가 발표됐어요");

        String longName = "가".repeat(100);                     // 단지 이름 최대 길이 (varchar 100)
        String title = AdminHousingService.resultTitle(longName);
        assertThat(title).hasSize(Notification.TITLE_MAX_LENGTH);
        assertThat(title).endsWith("… 청약 결과가 발표됐어요");
        assertThat(Notification.create(7L, "HOUSING_RESULT", title, "/user/demo-housing-subscription"))
                .isNotNull();                                      // 실제 알림 규칙을 통과한다

        String emojiAtCut = "가".repeat(85) + "🏠" + "가".repeat(10);   // 자르는 자리(86번째 칸)에 두 칸짜리 글자의 앞 칸
        assertThat(AdminHousingService.resultTitle(emojiAtCut)).doesNotContain("\uD83C…")
                .hasSizeLessThanOrEqualTo(Notification.TITLE_MAX_LENGTH);
    }

    @Test
    void 산정하지_않았으면_발표할_수_없다() {
        assertRejected(() -> serviceOn(ANNOUNCE_DAY).announce(ADMIN_ID, 1L), HousingErrorCode.NOT_COMPUTED);
        assertThat(complex.isAnnounced()).isFalse();
    }

    @Test
    void 발표일_전에는_발표할_수_없다() {
        complex.markComputed(LocalDateTime.of(2026, 11, 11, 9, 0));

        assertRejected(() -> serviceOn(LocalDate.of(2026, 11, 16)).announce(ADMIN_ID, 1L), HousingErrorCode.BEFORE_ANNOUNCE_DATE);
        verify(notificationService, never()).send(any(), any(String.class), any(), any());   // 종류가 String 인 옛 send 를 가리킨다 (NotificationType 버전이 생겨 any() 만으로는 모호하다, #289)
    }

    @Test
    void 이미_발표한_공고는_다시_발표하지_않는다() {
        complex.markComputed(LocalDateTime.of(2026, 11, 11, 9, 0));
        complex.announce(LocalDateTime.of(2026, 11, 17, 9, 0));

        assertRejected(() -> serviceOn(ANNOUNCE_DAY).announce(ADMIN_ID, 1L), HousingErrorCode.ALREADY_ANNOUNCED);
        verify(notificationService, times(0)).send(any(), any(String.class), any(), any());   // 종류가 String 인 옛 send 를 가리킨다 (NotificationType 버전이 생겨 any() 만으로는 모호하다, #289)
    }
}
