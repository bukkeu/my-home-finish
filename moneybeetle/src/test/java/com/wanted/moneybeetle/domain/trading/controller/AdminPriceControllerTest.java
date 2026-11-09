package com.wanted.moneybeetle.domain.trading.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.trading.exception.CsvInvalidException;
import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.exception.TradingExceptionHandler;
import com.wanted.moneybeetle.domain.trading.model.dto.PriceSaveResultDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.PriceTableDTO;
import com.wanted.moneybeetle.domain.trading.model.service.MarketAdminService;
import com.wanted.moneybeetle.domain.trading.model.service.PriceAdminService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : PM (#42)
*   AdminMarketController 가격표 부분 확인 (Service 는 가짜, DB·로그인 없이)
*   - 가격표·저장 응답 모양, CSV 내려받기 헤더, CSV 오류 응답(data.errors), 지난 턴 409
*  */
class AdminPriceControllerTest {

    private static final String PRICES = "/api/v1/admin/trading/seasons/7/prices";

    private final PriceAdminService priceService = mock(PriceAdminService.class);

    private MockMvc mockMvc() {
        var json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().build());
        return MockMvcBuilders
                .standaloneSetup(new AdminMarketController(mock(MarketAdminService.class), priceService))
                .setControllerAdvice(new TradingExceptionHandler(), new GlobalExceptionHandler())
                .setMessageConverters(new ByteArrayHttpMessageConverter(), json)
                .build();
    }

    @Test
    void 가격표는_currentTurn과_rows를_돌려준다() throws Exception {
        when(priceService.findPriceTable(7L)).thenReturn(new PriceTableDTO(5, List.of(
                new PriceTableDTO.Row(1L, "한빛반도체", Arrays.asList(76_800, 78_900, null)))));

        mockMvc().perform(get(PRICES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentTurn").value(5))
                .andExpect(jsonPath("$.data.rows[0].stockName").value("한빛반도체"))
                .andExpect(jsonPath("$.data.rows[0].prices[1]").value(78900))
                .andExpect(jsonPath("$.data.rows[0].prices[2]").doesNotExist());
    }

    @Test
    void 가격_저장은_saved와_warnings를_돌려준다() throws Exception {
        when(priceService.savePrices(eq(7L), any())).thenReturn(new PriceSaveResultDTO(0, List.of(
                new PriceSaveResultDTO.Warning(2L, 6, "한 턴에 ±30%를 넘는 변동이에요"))));

        mockMvc().perform(put(PRICES).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":false,\"cells\":[{\"stockId\":2,\"turnNo\":6,\"changeRate\":-35.0}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(0))
                .andExpect(jsonPath("$.data.warnings[0].stockId").value(2))
                .andExpect(jsonPath("$.data.warnings[0].turnNo").value(6));
    }

    @Test
    void 지난_턴을_고치면_409_PAST_TURN_LOCKED() throws Exception {
        when(priceService.savePrices(eq(7L), any()))
                .thenThrow(new BusinessException(TradingErrorCode.PAST_TURN_LOCKED));

        mockMvc().perform(put(PRICES).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":true,\"cells\":[{\"stockId\":1,\"turnNo\":3,\"price\":80000}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAST_TURN_LOCKED"));
    }

    @Test
    void CSV_내려받기는_text_csv_첨부파일() throws Exception {
        when(priceService.exportCsv(7L)).thenReturn("﻿stockId,stockName\r\n");

        mockMvc().perform(get(PRICES + "/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("season-7-prices.csv")));
    }

    @Test
    void CSV_올리기에_오류가_있으면_400과_오류_행_목록() throws Exception {
        when(priceService.importCsv(eq(7L), any(InputStream.class))).thenThrow(new CsvInvalidException(List.of(
                new CsvInvalidException.CsvError(3, "없는 종목 번호예요: 99"))));

        mockMvc().perform(multipart(PRICES + "/import")
                        .file(new MockMultipartFile("file", "prices.csv", "text/csv", "x".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CSV_INVALID"))
                .andExpect(jsonPath("$.data.errors[0].line").value(3))
                .andExpect(jsonPath("$.data.errors[0].message").value("없는 종목 번호예요: 99"));
    }

    @Test
    void CSV_올리기에_성공하면_saved() throws Exception {
        when(priceService.importCsv(eq(7L), any(InputStream.class))).thenReturn(new PriceSaveResultDTO(360, List.of()));

        mockMvc().perform(multipart(PRICES + "/import")
                        .file(new MockMultipartFile("file", "prices.csv", "text/csv", "x".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(360));
    }
}
