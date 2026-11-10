package com.wanted.moneybeetle.domain.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dto.AdminStockDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SectorDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SectorSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.StockSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.service.MarketAdminService;
import com.wanted.moneybeetle.domain.trading.model.service.PriceAdminService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : PM (#41)
*   AdminMarketController 확인 (Service 는 가짜, DB·로그인 없이)
*   - 주소·응답 JSON 모양 (API 명세서 121~126), 201 / 204, 요청 JSON 이 DTO 에 들어가는지, 에러 응답
*  */
class AdminMarketControllerTest {

    private static final String BASE = "/api/v1/admin/trading";

    private final MarketAdminService service = mock(MarketAdminService.class);

    private MockMvc mockMvc() {
        var json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().build());
        return MockMvcBuilders.standaloneSetup(new AdminMarketController(service, mock(PriceAdminService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(json)
                .build();
    }

    @Test
    void 분야_목록은_items_로_감싸고_isActive_이름으로_내보낸다() throws Exception {
        when(service.findSectors()).thenReturn(List.of(new SectorDTO(1L, "반도체", 1, true)));

        mockMvc().perform(get(BASE + "/sectors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sectorId").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("반도체"))
                .andExpect(jsonPath("$.data.items[0].isActive").value(true))
                .andExpect(jsonPath("$.data.items[0].active").doesNotExist());
    }

    @Test
    void 분야_등록은_201과_sectorId() throws Exception {
        when(service.createSector(any())).thenReturn(15L);

        mockMvc().perform(post(BASE + "/sectors").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"핀테크\",\"sortOrder\":15}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sectorId").value(15));
    }

    @Test
    void 분야_수정은_204이고_isActive_가_DTO에_들어간다() throws Exception {
        mockMvc().perform(put(BASE + "/sectors/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"핀테크\",\"isActive\":false}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<SectorSaveDTO> captor = ArgumentCaptor.forClass(SectorSaveDTO.class);
        verify(service).changeSector(eq(1L), captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("핀테크");
        assertThat(captor.getValue().getIsActive()).isFalse();
    }

    @Test
    void 종목_목록에는_시작가격과_기업정보가_들어간다() throws Exception {
        when(service.findStocks()).thenReturn(List.of(new AdminStockDTO(1L, 1L, "반도체", "한빛반도체", 76_800,
                "소개", "메모리", "1조 2,000억", "1,450억", true)));

        mockMvc().perform(get(BASE + "/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].stockId").value(1))
                .andExpect(jsonPath("$.data.items[0].sectorName").value("반도체"))
                .andExpect(jsonPath("$.data.items[0].startPrice").value(76800))
                .andExpect(jsonPath("$.data.items[0].revenue").value("1조 2,000억"))
                .andExpect(jsonPath("$.data.items[0].isActive").value(true));
    }

    @Test
    void 종목_등록은_201과_stockId_이고_요청값이_DTO에_들어간다() throws Exception {
        when(service.createStock(any())).thenReturn(1L);

        mockMvc().perform(post(BASE + "/stocks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectorId\":1,\"name\":\"한빛반도체\",\"startPrice\":76800,\"intro\":\"...\","
                                + "\"business\":\"...\",\"revenue\":\"1조 2,000억\",\"operatingProfit\":\"1,450억\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stockId").value(1));

        ArgumentCaptor<StockSaveDTO> captor = ArgumentCaptor.forClass(StockSaveDTO.class);
        verify(service).createStock(captor.capture());
        assertThat(captor.getValue().getSectorId()).isEqualTo(1L);
        assertThat(captor.getValue().getStartPrice()).isEqualTo(76_800);
        assertThat(captor.getValue().getOperatingProfit()).isEqualTo("1,450억");
    }

    @Test
    void 같은_이름의_종목은_409_STOCK_DUPLICATED() throws Exception {
        when(service.createStock(any())).thenThrow(new BusinessException(TradingErrorCode.STOCK_DUPLICATED));

        mockMvc().perform(post(BASE + "/stocks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectorId\":1,\"name\":\"한빛반도체\",\"startPrice\":76800}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STOCK_DUPLICATED"));
    }

    @Test
    void 종목_수정은_204() throws Exception {
        mockMvc().perform(put(BASE + "/stocks/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectorId\":1,\"name\":\"한빛반도체\",\"startPrice\":80000}"))
                .andExpect(status().isNoContent());

        verify(service).changeStock(eq(1L), any(StockSaveDTO.class));
    }
}
