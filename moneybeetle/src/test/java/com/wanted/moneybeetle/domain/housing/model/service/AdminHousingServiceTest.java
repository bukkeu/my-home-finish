package com.wanted.moneybeetle.domain.housing.model.service;

import com.wanted.moneybeetle.domain.housing.exception.HousingErrorCode;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingApplicationRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingComplexRepository;
import com.wanted.moneybeetle.domain.housing.model.dto.AdminComplexDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexRequestDTO;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import com.wanted.moneybeetle.domain.housing.model.policy.HousingWinnerPolicy;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.notification.model.service.NotificationService;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.global.config.TimeConfig;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* comment.
*   담당 : SCM (모의 청약)
*   AdminHousingService #47 확인 (Repository 는 가짜, 오늘은 2026-11-05 로 고정)
*   - 목록 : 신청자 수, 오늘 기준 상태
*   - 등록 : 빈 값·일정 순서 거절, 등록한 관리자 번호 저장
*   - 수정 : 신청자가 있으면 일정·세대 수 변경 거절, 정보만 바꾸는 건 허용 (SP-76)
*   - 삭제 : 신청자가 있으면 거절, 없으면 deleted_at 기록
*  */
class AdminHousingServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    private HousingComplexRepository complexRepository;
    private HousingApplicationRepository applicationRepository;
    private AdminHousingService service;
    private final AdminLogService adminLogService = mock(AdminLogService.class);

    @BeforeEach
    void setUp() {
        complexRepository = mock(HousingComplexRepository.class);
        applicationRepository = mock(HousingApplicationRepository.class);
        Clock fixed = Clock.fixed(TODAY.atStartOfDay(TimeConfig.SEOUL).toInstant(), TimeConfig.SEOUL);
        // 산정·발표용 의존성(#48)은 이 테스트에서 쓰지 않아 가짜만 넣는다 (AdminHousingResultServiceTest 에서 확인)
        service = new AdminHousingService(complexRepository, applicationRepository, fixed,
                new HousingWinnerPolicy(), mock(MemberService.class), mock(NotificationService.class), adminLogService);
    }

    // 공고 10/28, 신청 11/3 ~ 11/10, 발표 11/17, 120세대
    private ComplexRequestDTO request() {
        return new ComplexRequestDTO("비틀 파크", "서울 강서구", "84㎡", 650_000_000L, 120, "일반공급",
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3),
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 17));
    }

    private HousingComplex saved(long id) {
        ComplexRequestDTO r = request();
        HousingComplex complex = HousingComplex.create(r.getName(), r.getLocation(), r.getAreaType(),
                r.getSalePrice(), r.getSupplyUnits(), r.getSupplyType(), r.getNoticeDate(),
                r.getApplyStartDate(), r.getApplyEndDate(), r.getAnnounceDate(), ADMIN_ID);
        ReflectionTestUtils.setField(complex, "id", id);
        return complex;
    }

    @Test
    void 목록은_신청자_수와_오늘_기준_상태를_보여준다() {
        when(complexRepository.findByDeletedAtIsNullOrderByApplyStartDateDesc())
                .thenReturn(List.of(saved(1L), saved(2L)));
        when(applicationRepository.countGroupByHousingComplexIdIn(List.of(1L, 2L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 3L}));

        List<AdminComplexDTO> result = service.findComplexes();

        assertThat(result).extracting(AdminComplexDTO::getApplicants).containsExactly(3L, 0L);
        assertThat(result.get(0).getStatus()).isEqualTo(HousingComplex.STATUS_OPEN);   // 11/5 는 신청 기간
    }

    @Test
    void 등록하면_관리자_번호와_함께_저장하고_ID를_돌려준다() {
        when(complexRepository.save(any())).thenAnswer(inv -> {
            HousingComplex c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 9L);
            return c;
        });

        Long id = service.createComplex(ADMIN_ID, request());

        ArgumentCaptor<HousingComplex> captor = ArgumentCaptor.forClass(HousingComplex.class);
        verify(complexRepository).save(captor.capture());
        assertThat(id).isEqualTo(9L);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(ADMIN_ID);
        assertThat(captor.getValue().getStatus()).isEqualTo(HousingComplex.STATUS_UPCOMING);
    }

    @Test
    void 일정_순서가_틀리면_등록하지_않는다() {
        ComplexRequestDTO r = request();
        r.setApplyEndDate(LocalDate.of(2026, 11, 1));   // 마감일이 시작일보다 앞

        assertThatThrownBy(() -> service.createComplex(ADMIN_ID, r))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(HousingErrorCode.INVALID_SCHEDULE);
        verify(complexRepository, never()).save(any());
    }

    @Test
    void 빈_값이나_0_이하면_등록하지_않는다() {
        ComplexRequestDTO r = request();
        r.setSupplyUnits(0);

        assertThatThrownBy(() -> service.createComplex(ADMIN_ID, r))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(HousingErrorCode.INVALID_COMPLEX_INPUT);
    }

    @Test
    void 신청자가_있으면_일정을_바꾸는_수정은_거절한다() {
        HousingComplex complex = saved(1L);
        when(complexRepository.findForUpdate(1L)).thenReturn(Optional.of(complex));
        when(applicationRepository.countByHousingComplexId(1L)).thenReturn(2L);
        ComplexRequestDTO r = request();
        r.setAnnounceDate(LocalDate.of(2026, 11, 20));

        assertThatThrownBy(() -> service.updateComplex(ADMIN_ID, 1L, r))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(HousingErrorCode.COMPLEX_HAS_APPLICANTS);
        assertThat(complex.getAnnounceDate()).isEqualTo(LocalDate.of(2026, 11, 17));
    }

    @Test
    void 신청자가_있어도_정보만_바꾸는_수정은_된다() {
        HousingComplex complex = saved(1L);
        when(complexRepository.findForUpdate(1L)).thenReturn(Optional.of(complex));
        when(applicationRepository.countByHousingComplexId(1L)).thenReturn(2L);
        ComplexRequestDTO r = request();
        r.setName("비틀 파크 2차");

        service.updateComplex(ADMIN_ID, 1L, r);

        assertThat(complex.getName()).isEqualTo("비틀 파크 2차");
    }

    @Test
    void 신청자가_없으면_일정도_바꿀_수_있다() {
        HousingComplex complex = saved(1L);
        when(complexRepository.findForUpdate(1L)).thenReturn(Optional.of(complex));
        when(applicationRepository.countByHousingComplexId(1L)).thenReturn(0L);
        ComplexRequestDTO r = request();
        r.setSupplyUnits(200);

        service.updateComplex(ADMIN_ID, 1L, r);

        assertThat(complex.getSupplyUnits()).isEqualTo(200);
    }

    @Test
    void 없는_단지를_수정하면_404() {
        when(complexRepository.findForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateComplex(ADMIN_ID, 99L, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 신청자가_있으면_삭제하지_않는다() {
        HousingComplex complex = saved(1L);
        when(complexRepository.findForUpdate(1L)).thenReturn(Optional.of(complex));
        when(applicationRepository.countByHousingComplexId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteComplex(ADMIN_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(HousingErrorCode.COMPLEX_HAS_APPLICANTS);
        assertThat(complex.getDeletedAt()).isNull();
    }

    @Test
    void 신청자가_없으면_삭제_시각을_남긴다() {
        HousingComplex complex = saved(1L);
        when(complexRepository.findForUpdate(1L)).thenReturn(Optional.of(complex));
        when(applicationRepository.countByHousingComplexId(1L)).thenReturn(0L);

        service.deleteComplex(ADMIN_ID, 1L);

        assertThat(complex.getDeletedAt()).isEqualTo(LocalDateTime.of(2026, 11, 5, 0, 0));
    }
}
