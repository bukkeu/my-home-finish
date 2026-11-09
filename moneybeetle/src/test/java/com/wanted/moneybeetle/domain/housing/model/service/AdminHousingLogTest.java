package com.wanted.moneybeetle.domain.housing.model.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingApplicationRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingComplexRepository;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexRequestDTO;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import com.wanted.moneybeetle.domain.housing.model.policy.HousingWinnerPolicy;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.notification.model.service.NotificationService;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.global.config.TimeConfig;
import com.wanted.moneybeetle.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* comment.
*   담당 : SCM (모의 청약)
*   #178 관리자 작업 기록 : 등록·수정·삭제·산정·발표가 admin_log 에 "누가·무엇을·어느 단지에" 남는지 확인
*   - detailJson 은 올바른 JSON 이고, 수정은 변경 전·후를 담는다
*   - 규칙에 걸려 작업이 거절되면 기록하지 않는다
*  */
class AdminHousingLogTest {

    private static final Long ADMIN_ID = 1L;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HousingComplexRepository complexRepository = mock(HousingComplexRepository.class);
    private final HousingApplicationRepository applicationRepository = mock(HousingApplicationRepository.class);
    private final AdminLogService adminLogService = mock(AdminLogService.class);
    private HousingComplex complex;

    @BeforeEach
    void setUp() {
        complex = HousingComplex.create("비틀 파크", "서울 강서구", "84㎡", 650_000_000L, 2, "일반공급",
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3),
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 17), ADMIN_ID);
        ReflectionTestUtils.setField(complex, "id", 5L);
        when(complexRepository.findForUpdate(5L)).thenReturn(Optional.of(complex));
    }

    private AdminHousingService serviceOn(LocalDate today) {
        Clock fixed = Clock.fixed(today.atStartOfDay(TimeConfig.SEOUL).toInstant(), TimeConfig.SEOUL);
        return new AdminHousingService(complexRepository, applicationRepository, fixed,
                new HousingWinnerPolicy(new Random(1)), mock(MemberService.class), mock(NotificationService.class),
                adminLogService);
    }

    private ComplexRequestDTO request(String name) {
        return new ComplexRequestDTO(name, "서울 강서구", "84㎡", 650_000_000L, 2, "일반공급",
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3),
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 17));
    }

    // record(adminId, action, "HOUSING_COMPLEX", 5, detail) 가 한 번 불렸는지 보고 detail 을 JSON 으로 돌려준다
    private JsonNode recorded(String action) throws Exception {
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(adminLogService).record(eq(ADMIN_ID), eq(action), eq("HOUSING_COMPLEX"), eq(5L), detail.capture());
        return JSON.readTree(detail.getValue());
    }

    @Test
    void 공고_등록을_기록한다() throws Exception {
        when(complexRepository.save(any())).thenAnswer(inv -> {
            HousingComplex saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });

        serviceOn(LocalDate.of(2026, 10, 20)).createComplex(ADMIN_ID, request("비틀 파크"));

        JsonNode detail = recorded("HOUSING_COMPLEX_CREATE");
        assertThat(detail.at("/after/name").asText()).isEqualTo("비틀 파크");
        assertThat(detail.at("/after/applyEndDate").asText()).isEqualTo("2026-11-10");
    }

    @Test
    void 공고_수정은_변경_전과_후를_기록한다() throws Exception {
        serviceOn(LocalDate.of(2026, 10, 20)).updateComplex(ADMIN_ID, 5L, request("비틀 파크 2차"));

        JsonNode detail = recorded("HOUSING_COMPLEX_UPDATE");
        assertThat(detail.at("/before/name").asText()).isEqualTo("비틀 파크");
        assertThat(detail.at("/after/name").asText()).isEqualTo("비틀 파크 2차");
    }

    @Test
    void 공고_삭제를_기록한다() throws Exception {
        serviceOn(LocalDate.of(2026, 10, 20)).deleteComplex(ADMIN_ID, 5L);

        assertThat(recorded("HOUSING_COMPLEX_DELETE").at("/before/name").asText()).isEqualTo("비틀 파크");
    }

    @Test
    void 산정은_신청자_수와_당첨자_수를_기록한다() throws Exception {
        HousingApplication a = HousingApplication.create(7L, 5L, "MB-1", 0, 0, 10, 40, 0, 0, LocalDateTime.now());
        HousingApplication b = HousingApplication.create(8L, 5L, "MB-2", 0, 0, 10, 30, 0, 0, LocalDateTime.now());
        HousingApplication c = HousingApplication.create(9L, 5L, "MB-3", 0, 0, 10, 20, 0, 0, LocalDateTime.now());
        when(applicationRepository.findByHousingComplexId(5L)).thenReturn(List.of(a, b, c));

        serviceOn(LocalDate.of(2026, 11, 11)).compute(ADMIN_ID, 5L);

        JsonNode detail = recorded("HOUSING_COMPUTE");
        assertThat(detail.get("applicants").asInt()).isEqualTo(3);
        assertThat(detail.get("winners").asInt()).isEqualTo(2);
    }

    @Test
    void 발표는_알림_보낸_수를_기록한다() throws Exception {
        complex.markComputed(LocalDateTime.of(2026, 11, 11, 9, 0));
        when(applicationRepository.findByHousingComplexId(5L)).thenReturn(List.of(
                HousingApplication.create(7L, 5L, "MB-1", 0, 0, 10, 40, 0, 0, LocalDateTime.now())));

        serviceOn(LocalDate.of(2026, 11, 17)).announce(ADMIN_ID, 5L);

        assertThat(recorded("HOUSING_ANNOUNCE").get("notified").asInt()).isEqualTo(1);
    }

    @Test
    void 거절된_작업은_기록하지_않는다() {
        // 마감 전 산정 → APPLY_NOT_CLOSED
        assertThatThrownBy(() -> serviceOn(LocalDate.of(2026, 11, 10)).compute(ADMIN_ID, 5L))
                .isInstanceOf(BusinessException.class);
        // 신청자가 있는 공고 삭제 → COMPLEX_HAS_APPLICANTS
        when(applicationRepository.countByHousingComplexId(5L)).thenReturn(1L);
        assertThatThrownBy(() -> serviceOn(LocalDate.of(2026, 11, 5)).deleteComplex(ADMIN_ID, 5L))
                .isInstanceOf(BusinessException.class);

        verify(adminLogService, never()).record(anyLong(), anyString(), anyString(), anyLong(), any());
    }
}
