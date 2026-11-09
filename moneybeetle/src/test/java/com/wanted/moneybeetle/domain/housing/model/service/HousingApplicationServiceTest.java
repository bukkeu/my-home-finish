package com.wanted.moneybeetle.domain.housing.model.service;

import com.wanted.moneybeetle.domain.housing.exception.HousingErrorCode;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingApplicationRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingComplexRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.SubscriptionAccountRepository;
import com.wanted.moneybeetle.domain.housing.model.dto.ApplicationDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ApplicationResultDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ApplyResultDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ScoreDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ScoreRequestDTO;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import com.wanted.moneybeetle.domain.housing.model.entity.SubscriptionAccount;
import com.wanted.moneybeetle.domain.housing.model.policy.HousingScorePolicy;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.config.TimeConfig;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import com.wanted.moneybeetle.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* comment.
*   담당 : SCM (모의 청약)
*   HousingApplicationService #29 확인 (DB 없이 Repository 는 가짜, 오늘은 2026-11-05 로 고정)
*   단지 일정 : 신청 11/3 ~ 11/10, 발표 11/17
*  */
class HousingApplicationServiceTest {

    private static final Long MEMBER_ID = 8L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    private HousingComplexRepository complexRepository;
    private HousingApplicationRepository applicationRepository;
    private SubscriptionAccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        complexRepository = mock(HousingComplexRepository.class);
        applicationRepository = mock(HousingApplicationRepository.class);
        accountRepository = mock(SubscriptionAccountRepository.class);
        when(accountRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(account(48)));
        when(applicationRepository.saveAndFlush(any(HousingApplication.class))).thenAnswer(call -> {
            HousingApplication saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 40L);
            return saved;
        });
    }

    private HousingApplicationService serviceOn(LocalDate today) {
        Clock fixed = Clock.fixed(today.atTime(10, 0).atZone(TimeConfig.SEOUL).toInstant(), TimeConfig.SEOUL);
        return new HousingApplicationService(complexRepository, applicationRepository, accountRepository,
                new HousingScorePolicy(), fixed);
    }

    private SubscriptionAccount account(int paidCount) {
        SubscriptionAccount account = SubscriptionAccount.create(MEMBER_ID);
        ReflectionTestUtils.setField(account, "paidCount", paidCount);
        return account;
    }

    private HousingComplex complex(String status) {
        HousingComplex complex = BeanUtils.instantiateClass(HousingComplex.class);
        ReflectionTestUtils.setField(complex, "id", 1L);
        ReflectionTestUtils.setField(complex, "name", "그린숲 행복주택");
        ReflectionTestUtils.setField(complex, "status", status);
        ReflectionTestUtils.setField(complex, "applyStartDate", LocalDate.of(2026, 11, 3));
        ReflectionTestUtils.setField(complex, "applyEndDate", LocalDate.of(2026, 11, 10));
        ReflectionTestUtils.setField(complex, "announceDate", LocalDate.of(2026, 11, 17));
        return complex;
    }

    private HousingApplication application(Long memberId, String result) {
        HousingApplication app = HousingApplication.create(memberId, 1L, "MB-261105-0042", 3, 1, 48,
                8, 10, 6, LocalDateTime.of(2026, 11, 5, 10, 0));
        ReflectionTestUtils.setField(app, "id", 40L);
        ReflectionTestUtils.setField(app, "result", result);
        return app;
    }

    private void assertRejected(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    // ── API 68 가점 계산기 ──

    @Test
    void 가점_계산은_입력값과_내_통장_납입회차로_계산하고_저장하지_않는다() {
        ScoreDTO score = serviceOn(TODAY).previewScore(MEMBER_ID, new ScoreRequestDTO(3, 1));

        assertThat(score.getTotalScore()).isEqualTo(24);     // 8 + 10 + 6 (48회)
        assertThat(score.getBaseDate()).isEqualTo(TODAY);
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void 음수_입력은_INVALID_SCORE_INPUT() {
        assertRejected(() -> serviceOn(TODAY).previewScore(MEMBER_ID, new ScoreRequestDTO(-1, 0)),
                HousingErrorCode.INVALID_SCORE_INPUT);
        assertRejected(() -> serviceOn(TODAY).apply(MEMBER_ID, 1L, new ScoreRequestDTO(0, -2)),
                HousingErrorCode.INVALID_SCORE_INPUT);
    }

    // ── API 69 청약 신청 ──

    @Test
    void 신청_기간_안이면_신청_시점의_점수를_저장하고_접수번호를_준다() {
        when(complexRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(complex("OPEN")));
        when(applicationRepository.countByAppliedAtGreaterThanEqual(TODAY.atStartOfDay())).thenReturn(41L);

        ApplyResultDTO result = serviceOn(TODAY).apply(MEMBER_ID, 1L, new ScoreRequestDTO(3, 1));

        assertThat(result.getApplicationId()).isEqualTo(40L);
        assertThat(result.getReceiptNo()).isEqualTo("MB-261105-0042");   // 오늘 42번째
        assertThat(result.getTotalScore()).isEqualTo(24);

        ArgumentCaptor<HousingApplication> saved = ArgumentCaptor.forClass(HousingApplication.class);
        verify(applicationRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPaidCountSnapshot()).isEqualTo(48);
        assertThat(saved.getValue().getHomelessScore()).isEqualTo(8);
        assertThat(saved.getValue().getResult()).isNull();               // 발표 전
    }

    @Test
    void 신청_기간_밖이면_NOT_IN_APPLY_PERIOD_시작일_마감일_당일은_가능() {
        when(complexRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(complex("OPEN")));

        assertRejected(() -> serviceOn(LocalDate.of(2026, 11, 2)).apply(MEMBER_ID, 1L, new ScoreRequestDTO(3, 1)),
                HousingErrorCode.NOT_IN_APPLY_PERIOD);
        assertRejected(() -> serviceOn(LocalDate.of(2026, 11, 11)).apply(MEMBER_ID, 1L, new ScoreRequestDTO(3, 1)),
                HousingErrorCode.NOT_IN_APPLY_PERIOD);

        assertThat(serviceOn(LocalDate.of(2026, 11, 3)).apply(MEMBER_ID, 1L, new ScoreRequestDTO(3, 1))).isNotNull();
        assertThat(serviceOn(LocalDate.of(2026, 11, 10)).apply(MEMBER_ID, 1L, new ScoreRequestDTO(3, 1))).isNotNull();
    }

    @Test
    void 같은_단지에_두_번_신청하면_ALREADY_APPLIED() {
        when(complexRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(complex("OPEN")));
        when(applicationRepository.existsByMemberIdAndHousingComplexId(MEMBER_ID, 1L)).thenReturn(true);

        assertRejected(() -> serviceOn(TODAY).apply(MEMBER_ID, 1L, new ScoreRequestDTO(3, 1)),
                HousingErrorCode.ALREADY_APPLIED);
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시에_신청해_DB_유니크_키에_걸려도_ALREADY_APPLIED() {
        when(complexRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(complex("OPEN")));
        when(applicationRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk"));

        assertRejected(() -> serviceOn(TODAY).apply(MEMBER_ID, 1L, new ScoreRequestDTO(3, 1)),
                HousingErrorCode.ALREADY_APPLIED);
    }

    @Test
    void 없는_단지는_NOT_FOUND() {
        when(complexRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());
        assertRejected(() -> serviceOn(TODAY).apply(MEMBER_ID, 99L, new ScoreRequestDTO(3, 1)),
                CommonErrorCode.NOT_FOUND);
    }

    // ── API 70 내 신청 내역 ──

    @Test
    void 내_신청_내역은_발표_전이면_결과를_감춘다() {
        when(applicationRepository.findByMemberId(MEMBER_ID, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(application(MEMBER_ID, "WIN")), PageRequest.of(0, 20), 1));
        when(complexRepository.findAllById(anyList())).thenReturn(List.of(complex("OPEN")));

        PageResponse<ApplicationDTO> page = serviceOn(TODAY).findMyApplications(MEMBER_ID, PageRequest.of(0, 20));

        ApplicationDTO item = page.getItems().get(0);
        assertThat(item.getComplexName()).isEqualTo("그린숲 행복주택");
        assertThat(item.getAnnounceDate()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(item.getResult()).isNull();          // 결과가 저장돼 있어도 발표 전에는 null
    }

    // ── API 71 당첨 결과 ──

    @Test
    void 발표_뒤에는_결과와_문구를_준다() {
        when(applicationRepository.findById(40L)).thenReturn(Optional.of(application(MEMBER_ID, "LOSE")));
        when(complexRepository.findById(1L)).thenReturn(Optional.of(complex("ANNOUNCED")));

        ApplicationResultDTO result = serviceOn(TODAY).getResult(MEMBER_ID, 40L);

        assertThat(result.getResult()).isEqualTo("LOSE");
        assertThat(result.getMessage()).isEqualTo("이번에는 낙첨이에요. 내 가점은 24점이었어요");
    }

    @Test
    void 발표_전이거나_산정_전이면_NOT_ANNOUNCED_YET() {
        when(applicationRepository.findById(40L)).thenReturn(Optional.of(application(MEMBER_ID, "WIN")));
        when(complexRepository.findById(1L)).thenReturn(Optional.of(complex("CLOSED")));
        assertRejected(() -> serviceOn(TODAY).getResult(MEMBER_ID, 40L), HousingErrorCode.NOT_ANNOUNCED_YET);

        when(applicationRepository.findById(40L)).thenReturn(Optional.of(application(MEMBER_ID, null)));
        when(complexRepository.findById(1L)).thenReturn(Optional.of(complex("ANNOUNCED")));
        assertRejected(() -> serviceOn(TODAY).getResult(MEMBER_ID, 40L), HousingErrorCode.NOT_ANNOUNCED_YET);
    }

    @Test
    void 남의_신청_결과는_NOT_FOUND() {
        when(applicationRepository.findById(40L)).thenReturn(Optional.of(application(7L, "WIN")));
        assertRejected(() -> serviceOn(TODAY).getResult(MEMBER_ID, 40L), CommonErrorCode.NOT_FOUND);
    }
}
