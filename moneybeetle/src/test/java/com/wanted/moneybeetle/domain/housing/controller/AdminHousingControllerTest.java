package com.wanted.moneybeetle.domain.housing.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.housing.exception.HousingErrorCode;
import com.wanted.moneybeetle.domain.housing.model.dto.AdminComplexDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.AnnounceResultDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ApplicantDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComputeResultDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexRequestDTO;
import com.wanted.moneybeetle.domain.housing.model.service.AdminHousingService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* comment.
*   담당 : SCM (모의 청약)
*   AdminHousingController #47·#48 확인 (Service 는 가짜, 관리자 1번으로 로그인한 상태)
*   - 주소·본문 연결, 응답 코드(200·201·204)와 JSON 모양 (API 명세서 142~145)
*   - 등록한 관리자 번호는 세션에서 꺼내 Service 에 넘긴다
*   ADMIN 만 들어올 수 있는지는 SecurityConfig(/api/v1/admin/**) 가 맡는다
*  */
class AdminHousingControllerTest {

    private static final Long ADMIN_ID = 1L;
    private static final String BODY = """
            {"name":"비틀 파크","location":"서울 강서구","areaType":"84㎡","salePrice":650000000,
             "supplyUnits":120,"supplyType":"일반공급","noticeDate":"2026-10-28",
             "applyStartDate":"2026-11-03","applyEndDate":"2026-11-10","announceDate":"2026-11-17"}
            """;

    private final AdminHousingService adminHousingService = mock(AdminHousingService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(ADMIN_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, List.of()));

        var json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminHousingController(adminHousingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(json)
                .build();
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 단지_목록은_items_안에_신청자_수와_함께() throws Exception {
        when(adminHousingService.findComplexes()).thenReturn(List.of(new AdminComplexDTO(1L, "비틀 파크",
                "서울 강서구", "84㎡", 650_000_000L, 120, "일반공급", LocalDate.of(2026, 10, 28),
                LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 17),
                "OPEN", 3L, false)));

        mockMvc.perform(get("/api/v1/admin/housing/complexes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].complexId").value(1))
                .andExpect(jsonPath("$.data.items[0].applyStartDate").value("2026-11-03"))
                .andExpect(jsonPath("$.data.items[0].applicants").value(3))
                .andExpect(jsonPath("$.data.items[0].computed").value(false));
    }

    @Test
    void 등록은_201과_complexId_그리고_관리자_번호를_넘긴다() throws Exception {
        when(adminHousingService.createComplex(eq(ADMIN_ID), any())).thenReturn(9L);

        mockMvc.perform(post("/api/v1/admin/housing/complexes")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.complexId").value(9));

        ArgumentCaptor<ComplexRequestDTO> captor = ArgumentCaptor.forClass(ComplexRequestDTO.class);
        verify(adminHousingService).createComplex(eq(ADMIN_ID), captor.capture());
        assertThat(captor.getValue().getApplyEndDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(captor.getValue().getSupplyUnits()).isEqualTo(120);
    }

    @Test
    void 일정_순서가_틀리면_400() throws Exception {
        when(adminHousingService.createComplex(eq(ADMIN_ID), any()))
                .thenThrow(new BusinessException(HousingErrorCode.INVALID_SCHEDULE));

        mockMvc.perform(post("/api/v1/admin/housing/complexes")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SCHEDULE"));
    }

    @Test
    void 수정은_204_본문_없음() throws Exception {
        mockMvc.perform(put("/api/v1/admin/housing/complexes/3")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(adminHousingService).updateComplex(eq(ADMIN_ID), eq(3L), any());
    }

    @Test
    void 신청자가_있는_공고를_삭제하면_409() throws Exception {
        doThrow(new BusinessException(HousingErrorCode.COMPLEX_HAS_APPLICANTS))
                .when(adminHousingService).deleteComplex(ADMIN_ID, 3L);

        mockMvc.perform(delete("/api/v1/admin/housing/complexes/3"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COMPLEX_HAS_APPLICANTS"));
    }

    @Test
    void 삭제는_204() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/housing/complexes/3"))
                .andExpect(status().isNoContent());

        verify(adminHousingService).deleteComplex(ADMIN_ID, 3L);
    }

    // ── #48 신청자 목록·산정·발표 (API 146~148) ──

    @Test
    void 신청자_목록은_items_안에_닉네임_가점_결과() throws Exception {
        when(adminHousingService.findApplicants(1L)).thenReturn(List.of(new ApplicantDTO(40L, "민지",
                "MB-261105-0001", 24, 14, LocalDateTime.of(2026, 11, 5, 9, 0), null, null)));

        mockMvc.perform(get("/api/v1/admin/housing/complexes/1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].applicationId").value(40))
                .andExpect(jsonPath("$.data.items[0].nickname").value("민지"))
                .andExpect(jsonPath("$.data.items[0].totalScore").value(24))
                .andExpect(jsonPath("$.data.items[0].paidCount").value(14));
    }

    @Test
    void 산정은_신청자_수와_당첨자_수를_돌려준다() throws Exception {
        when(adminHousingService.compute(ADMIN_ID, 1L))
                .thenReturn(new ComputeResultDTO(8, 2, LocalDateTime.of(2026, 11, 11, 9, 0)));

        mockMvc.perform(post("/api/v1/admin/housing/complexes/1/compute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicants").value(8))
                .andExpect(jsonPath("$.data.winners").value(2))
                .andExpect(jsonPath("$.data.computedAt").value("2026-11-11T09:00:00"));
    }

    @Test
    void 이미_산정한_공고를_다시_산정하면_409() throws Exception {
        when(adminHousingService.compute(ADMIN_ID, 1L)).thenThrow(new BusinessException(HousingErrorCode.ALREADY_COMPUTED));

        mockMvc.perform(post("/api/v1/admin/housing/complexes/1/compute"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_COMPUTED"));
    }

    @Test
    void 발표는_발표_시각과_알림_보낸_수를_돌려준다() throws Exception {
        when(adminHousingService.announce(ADMIN_ID, 1L))
                .thenReturn(new AnnounceResultDTO(LocalDateTime.of(2026, 11, 17, 10, 0), 8));

        mockMvc.perform(post("/api/v1/admin/housing/complexes/1/announce"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.announcedAt").value("2026-11-17T10:00:00"))
                .andExpect(jsonPath("$.data.notified").value(8));
    }

    @Test
    void 발표일_전에_발표하면_409() throws Exception {
        when(adminHousingService.announce(ADMIN_ID, 1L))
                .thenThrow(new BusinessException(HousingErrorCode.BEFORE_ANNOUNCE_DATE));

        mockMvc.perform(post("/api/v1/admin/housing/complexes/1/announce"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BEFORE_ANNOUNCE_DATE"));
    }
}
