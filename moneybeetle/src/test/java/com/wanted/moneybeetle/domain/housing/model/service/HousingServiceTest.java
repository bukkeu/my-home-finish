package com.wanted.moneybeetle.domain.housing.model.service;

import com.wanted.moneybeetle.domain.growth.model.service.RewardService;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingApplicationRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.SubscriptionAccountRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.SubscriptionDepositRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingComplexRepository;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexDetailDTO;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import com.wanted.moneybeetle.global.config.TimeConfig;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* comment.
*   담당 : SCM (모의 청약)
*   HousingService #27 확인 (DB 없이 Repository 는 가짜(mock), 오늘 날짜는 고정 시계)
*   - 캘린더 : 그 달 첫날·말일로 조회, 오늘 기준 상태, 내가 신청한 단지만 applied = true
*   - 상세   : 없는 단지 404, 경쟁률은 마감 뒤에만, 내 신청 내역
*  */
class HousingServiceTest {

    private static final Long MEMBER_ID = 8L;

    private HousingComplexRepository complexRepository;
    private HousingApplicationRepository applicationRepository;

    @BeforeEach
    void setUp() {
        complexRepository = mock(HousingComplexRepository.class);
        applicationRepository = mock(HousingApplicationRepository.class);
    }

    // 오늘을 지정한 날짜로 고정한 서비스
    private HousingService serviceOn(LocalDate today) {
        Clock fixed = Clock.fixed(today.atStartOfDay(TimeConfig.SEOUL).toInstant(), TimeConfig.SEOUL);
        // 통장 관련 의존성(#28)은 이 테스트에서 쓰지 않아 가짜만 넣는다
        return new HousingService(complexRepository, applicationRepository, fixed,
                mock(SubscriptionAccountRepository.class), mock(SubscriptionDepositRepository.class),
                mock(RewardService.class), mock(com.wanted.moneybeetle.domain.housing.model.policy.DepositLimitPolicy.class));
    }

    // 신청 11/3 ~ 11/10, 발표 11/17, 120세대 단지 (엔티티 생성 메서드는 #47 에서 만들므로 테스트에서는 값을 직접 넣는다)
    private HousingComplex complex(long id, String status) {
        HousingComplex complex = BeanUtils.instantiateClass(HousingComplex.class);
        ReflectionTestUtils.setField(complex, "housingComplexId", id);
        ReflectionTestUtils.setField(complex, "name", "단지" + id);
        ReflectionTestUtils.setField(complex, "status", status);
        ReflectionTestUtils.setField(complex, "supplyUnits", 120);
        ReflectionTestUtils.setField(complex, "noticeDate", LocalDate.of(2026, 10, 28));
        ReflectionTestUtils.setField(complex, "applyStartDate", LocalDate.of(2026, 11, 3));
        ReflectionTestUtils.setField(complex, "applyEndDate", LocalDate.of(2026, 11, 10));
        ReflectionTestUtils.setField(complex, "announceDate", LocalDate.of(2026, 11, 17));
        return complex;
    }

    private HousingApplication application(long id, long complexId) {
        HousingApplication application = BeanUtils.instantiateClass(HousingApplication.class);
        ReflectionTestUtils.setField(application, "housingApplicationId", id);
        ReflectionTestUtils.setField(application, "memberId", MEMBER_ID);
        ReflectionTestUtils.setField(application, "housingComplexId", complexId);
        ReflectionTestUtils.setField(application, "receiptNo", "MB-261105-0042");
        ReflectionTestUtils.setField(application, "totalScore", 24);
        ReflectionTestUtils.setField(application, "appliedAt", LocalDateTime.of(2026, 11, 5, 10, 0));
        return application;
    }

    @Test
    void 캘린더는_그_달_첫날과_말일로_조회하고_내가_신청한_단지만_applied() {
        when(complexRepository.findByNoticeDateLessThanEqualAndAnnounceDateGreaterThanEqualAndDeletedAtIsNullOrderByApplyStartDate(
                LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1)))
                .thenReturn(List.of(complex(1, "UPCOMING"), complex(2, "UPCOMING")));
        when(applicationRepository.findByMemberIdAndHousingComplexIdIn(eq(MEMBER_ID), anyList()))
                .thenReturn(List.of(application(40, 2)));

        List<ComplexDTO> items = serviceOn(LocalDate.of(2026, 11, 5)).findCalendar(YearMonth.of(2026, 11), MEMBER_ID);

        assertThat(items).extracting(ComplexDTO::getComplexId).containsExactly(1L, 2L);
        assertThat(items).extracting(ComplexDTO::isApplied).containsExactly(false, true);
        assertThat(items).extracting(ComplexDTO::getStatus).containsOnly("OPEN");   // 11/5 는 신청 기간 안
    }

    @Test
    void 상태는_DB값이_아니라_오늘_날짜로_계산한다() {
        HousingComplex complex = complex(1, "UPCOMING");
        assertThat(complex.currentStatus(LocalDate.of(2026, 11, 2))).isEqualTo("UPCOMING");
        assertThat(complex.currentStatus(LocalDate.of(2026, 11, 3))).isEqualTo("OPEN");     // 시작일 당일
        assertThat(complex.currentStatus(LocalDate.of(2026, 11, 10))).isEqualTo("OPEN");    // 마감일 당일
        assertThat(complex.currentStatus(LocalDate.of(2026, 11, 11))).isEqualTo("CLOSED");
        assertThat(complex(2, "ANNOUNCED").currentStatus(LocalDate.of(2026, 11, 5))).isEqualTo("ANNOUNCED");
    }

    @Test
    void 없거나_삭제된_단지는_NOT_FOUND() {
        when(complexRepository.findByHousingComplexIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceOn(LocalDate.of(2026, 11, 5)).getComplex(99L, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 접수_마감_전에는_경쟁률이_null이고_신청자_수도_세지_않는다() {
        when(complexRepository.findByHousingComplexIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(complex(1, "OPEN")));
        when(applicationRepository.findByMemberIdAndHousingComplexId(MEMBER_ID, 1L)).thenReturn(Optional.empty());

        ComplexDetailDTO detail = serviceOn(LocalDate.of(2026, 11, 10)).getComplex(1L, MEMBER_ID);   // 마감일 당일

        assertThat(detail.getCompetitionRate()).isNull();
        assertThat(detail.getStatus()).isEqualTo("OPEN");
        assertThat(detail.getMyApplication()).isNull();
        verify(applicationRepository, never()).countByHousingComplexId(any());
    }

    @Test
    void 접수_마감_뒤에는_경쟁률을_소수_둘째_자리까지_보여준다() {
        when(complexRepository.findByHousingComplexIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(complex(1, "OPEN")));
        when(applicationRepository.countByHousingComplexId(1L)).thenReturn(250L);   // 250명 / 120세대
        when(applicationRepository.findByMemberIdAndHousingComplexId(MEMBER_ID, 1L))
                .thenReturn(Optional.of(application(40, 1)));

        ComplexDetailDTO detail = serviceOn(LocalDate.of(2026, 11, 11)).getComplex(1L, MEMBER_ID);

        assertThat(detail.getCompetitionRate()).isEqualTo(2.08);
        assertThat(detail.getStatus()).isEqualTo("CLOSED");
        assertThat(detail.getMyApplication().getReceiptNo()).isEqualTo("MB-261105-0042");
        assertThat(detail.getMyApplication().getTotalScore()).isEqualTo(24);
    }
}
