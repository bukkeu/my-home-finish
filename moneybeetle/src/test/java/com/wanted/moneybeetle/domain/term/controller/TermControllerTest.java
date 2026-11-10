package com.wanted.moneybeetle.domain.term.controller;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.term.exception.TermErrorCode;
import com.wanted.moneybeetle.domain.term.model.dto.AdminTermDTO;
import com.wanted.moneybeetle.domain.term.model.dto.AdminTermListDTO;
import com.wanted.moneybeetle.domain.term.model.dto.EconomicTermDTO;
import com.wanted.moneybeetle.domain.term.model.dto.ScheduledTermDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TermCreateDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TermCreatedDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TermScheduleDayDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TermScheduleListDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TodayTermsDTO;
import com.wanted.moneybeetle.domain.term.model.service.EconomicTermService;
import com.wanted.moneybeetle.domain.term.model.service.TermService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TermControllerTest {

    private TermService termService;
    private EconomicTermService economicTermService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        termService = mock(TermService.class);
        economicTermService = mock(EconomicTermService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TermController(termService),
                        new AdminTermController(economicTermService, termService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()))
                .build();
    }

    @Test
    @DisplayName("오늘의 경제 용어: date 와 terms (API 20)")
    void getsToday() throws Exception {
        when(termService.getToday()).thenReturn(new TodayTermsDTO(LocalDate.of(2026, 11, 5),
                List.of(new EconomicTermDTO(3L, "복리", "이자에 이자가 붙는 방식"))));

        mockMvc.perform(get("/api/v1/economic-terms/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-11-05"))
                .andExpect(jsonPath("$.data.terms[0].economicTermId").value(3))
                .andExpect(jsonPath("$.data.terms[0].term").value("복리"));
    }

    @Test
    @DisplayName("관리자 용어 목록: items 에 isActive (API 95)")
    void listsTerms() throws Exception {
        when(economicTermService.findTerms("복", 0, 20)).thenReturn(
                new AdminTermListDTO(List.of(new AdminTermDTO(3L, "복리", "뜻", true))));

        mockMvc.perform(get("/api/v1/admin/economic-terms").param("keyword", "복").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].economicTermId").value(3))
                .andExpect(jsonPath("$.data.items[0].isActive").value(true));
    }

    @Test
    @DisplayName("관리자 용어 등록: 201 과 economicTermId, 중복이면 409 TERM_DUPLICATED (API 96)")
    void createsTerm() throws Exception {
        when(economicTermService.create(new TermCreateDTO("복리", "이자에 이자가 붙는 방식")))
                .thenReturn(new TermCreatedDTO(3L));

        mockMvc.perform(post("/api/v1/admin/economic-terms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"term\":\"복리\",\"description\":\"이자에 이자가 붙는 방식\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.economicTermId").value(3));

        when(economicTermService.create(any(TermCreateDTO.class)))
                .thenThrow(new BusinessException(TermErrorCode.TERM_DUPLICATED));
        mockMvc.perform(post("/api/v1/admin/economic-terms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"term\":\"복리\",\"description\":\"뜻\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TERM_DUPLICATED"));
    }

    @Test
    @DisplayName("관리자 용어 수정·삭제: 204 (API 97·98)")
    void updatesAndDeletes() throws Exception {
        mockMvc.perform(put("/api/v1/admin/economic-terms/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"term\":\"복리\",\"description\":\"뜻\",\"isActive\":false}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/economic-terms/3")).andExpect(status().isNoContent());

        verify(economicTermService).update(eq(3L), any());
        verify(economicTermService).delete(3L);
    }

    @Test
    @DisplayName("월별 일정: days 에 isAuto 와 terms (API 99)")
    void getsSchedules() throws Exception {
        when(termService.findMonth(YearMonth.of(2026, 11))).thenReturn(new TermScheduleListDTO(List.of(
                new TermScheduleDayDTO(LocalDate.of(2026, 11, 5), false, List.of(new ScheduledTermDTO(3L, "복리"))))));

        mockMvc.perform(get("/api/v1/admin/term-schedules").param("month", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].date").value("2026-11-05"))
                .andExpect(jsonPath("$.data.days[0].isAuto").value(false))
                .andExpect(jsonPath("$.data.days[0].terms[0].term").value("복리"));
    }

    @Test
    @DisplayName("오늘의 용어 지정: 204, 지난 날짜는 400 PAST_DATE_LOCKED (API 100)")
    void assignsSchedule() throws Exception {
        mockMvc.perform(put("/api/v1/admin/term-schedules/2026-11-10").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"economicTermIds\":[3,5,8,11,14]}"))
                .andExpect(status().isNoContent());
        verify(termService).assign(LocalDate.of(2026, 11, 10), List.of(3L, 5L, 8L, 11L, 14L));

        doThrow(new BusinessException(TermErrorCode.PAST_DATE_LOCKED)).when(termService)
                .assign(eq(LocalDate.of(2026, 11, 1)), any());
        mockMvc.perform(put("/api/v1/admin/term-schedules/2026-11-01").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"economicTermIds\":[3,5,8,11,14]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PAST_DATE_LOCKED"));
    }
}
