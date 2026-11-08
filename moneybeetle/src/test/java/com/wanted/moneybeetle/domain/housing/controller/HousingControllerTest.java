package com.wanted.moneybeetle.domain.housing.controller;

import com.wanted.moneybeetle.domain.housing.model.dto.ComplexDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexDetailDTO;
import com.wanted.moneybeetle.domain.housing.model.service.HousingService;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import com.wanted.moneybeetle.global.security.LevelCheckInterceptor;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* comment.
*   담당 : SCM (성장·홈·모의 청약)
*   HousingController #27 확인 (Service 는 가짜, DB·로그인 없이)
*   - 주소·파라미터 연결과 응답 JSON 모양 (API 명세서 63·64)
*   - @RequireLevel(HOUSING) : 레벨 조회 연동(#4 TODO) 전이라 지금은 LEVEL_LOCKED 로 막힌다
*  */
class HousingControllerTest {

    private final HousingService housingService = mock(HousingService.class);

    private MockMvc mockMvc(boolean withLevelCheck) {
        // Spring Boot 서버와 같은 JSON 설정 (Boot 는 날짜를 "2026-11-03" 글자로 내보낸다)
        var json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
        var builder = MockMvcBuilders.standaloneSetup(new HousingController(housingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(json);
        if (withLevelCheck) {
            builder.addInterceptors(new LevelCheckInterceptor());
        }
        return builder.build();
    }

    @Test
    void 캘린더는_month_로_조회하고_items_로_감싸서_돌려준다() throws Exception {
        when(housingService.findCalendar(eq(YearMonth.of(2026, 11)), any())).thenReturn(List.of(
                new ComplexDTO(1L, "그린숲 행복주택", LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3),
                        LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 17), "OPEN", false)));

        mockMvc(false).perform(get("/api/v1/housing/complexes").param("month", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].complexId").value(1))
                .andExpect(jsonPath("$.data.items[0].applyStartDate").value("2026-11-03"))
                .andExpect(jsonPath("$.data.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data.items[0].applied").value(false));
    }

    @Test
    void 공고_상세는_주소의_complexId_로_조회한다() throws Exception {
        when(housingService.getComplex(eq(1L), any())).thenReturn(new ComplexDetailDTO(
                1L, "그린숲 행복주택", "가상시 덕양구", "59㎡", 320000000L, 120, "일반공급",
                LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 17),
                "OPEN", null, null));

        mockMvc(false).perform(get("/api/v1/housing/complexes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complexId").value(1))
                .andExpect(jsonPath("$.data.salePrice").value(320000000))
                .andExpect(jsonPath("$.data.competitionRate").doesNotExist())
                .andExpect(jsonPath("$.data.myApplication").doesNotExist());
    }

    @Test
    void 레벨_조회_연동_전에는_LEVEL_LOCKED_403() throws Exception {
        mockMvc(true).perform(get("/api/v1/housing/complexes/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("LEVEL_LOCKED"));
    }
}
