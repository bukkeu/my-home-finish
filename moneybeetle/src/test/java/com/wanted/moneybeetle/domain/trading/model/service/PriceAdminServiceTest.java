package com.wanted.moneybeetle.domain.trading.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.trading.exception.CsvInvalidException;
import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dao.StockPriceRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.AdminStockDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.PriceCellDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.PriceSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.PriceSaveResultDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.PriceTableDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : PM (#42)
*   PriceAdminService 확인 (DB 없이 Repository 는 가짜(mock))
*   - 가격표 모양, 지난 턴 잠금, 변동률 입력(50원 반올림), ±30% 경고와 confirm, 0원 이하 거절, 변동률 재계산
*   - CSV 내려받기 모양, 올리기 성공·오류 행(전체 취소)
*  */
class PriceAdminServiceTest {

    private static final Long SEASON_ID = 7L;

    private TradingSeasonRepository seasonRepository;
    private StockRepository stockRepository;
    private StockPriceRepository priceRepository;
    private MarketAdminService marketAdminService;
    private PriceAdminService service;

    private TradingSeason season;
    private final List<StockPrice> savedPrices = new ArrayList<>();
    private final Stock hanbit = stock(1L, "한빛반도체", 10_000);

    @BeforeEach
    void setUp() {
        seasonRepository = mock(TradingSeasonRepository.class);
        stockRepository = mock(StockRepository.class);
        priceRepository = mock(StockPriceRepository.class);
        marketAdminService = mock(MarketAdminService.class);
        service = new PriceAdminService(seasonRepository, stockRepository, priceRepository, marketAdminService);

        season = TradingSeason.create("시즌 7", LocalDate.of(2026, 10, 19), new BigDecimal("100.00"), null, 1L);
        ReflectionTestUtils.setField(season, "tradingSeasonId", SEASON_ID);
        when(seasonRepository.findById(SEASON_ID)).thenReturn(Optional.of(season));
        when(stockRepository.findAllById(anyIterable())).thenReturn(List.of(hanbit));
        when(stockRepository.findAll()).thenReturn(List.of(hanbit));
        when(priceRepository.findAllByTradingSeasonId(SEASON_ID)).thenReturn(savedPrices);
        when(marketAdminService.findStocks()).thenReturn(List.of(new AdminStockDTO(1L, 1L, "반도체", "한빛반도체",
                10_000, null, null, null, null, true)));
    }

    private static Stock stock(long id, String name, int startPrice) {
        Stock stock = Stock.create(1L, name, startPrice);
        ReflectionTestUtils.setField(stock, "stockId", id);
        return stock;
    }

    private void currentTurn(int turn) {
        ReflectionTestUtils.setField(season, "currentTurn", turn);
    }

    private StockPrice existingPrice(int turn, int price) {
        StockPrice row = StockPrice.create(SEASON_ID, 1L, turn, price, null);
        savedPrices.add(row);
        return row;
    }

    private PriceSaveResultDTO save(boolean confirm, PriceCellDTO... cells) {
        return service.savePrices(SEASON_ID, new PriceSaveDTO(confirm, List.of(cells)));
    }

    @SuppressWarnings("unchecked")
    private List<StockPrice> insertedRows() {
        ArgumentCaptor<List<StockPrice>> captor = ArgumentCaptor.forClass(List.class);
        verify(priceRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void 가격표는_종목마다_12칸이고_빈_턴은_null() {
        currentTurn(2);
        existingPrice(1, 10_000);
        existingPrice(2, 10_500);

        PriceTableDTO table = service.findPriceTable(SEASON_ID);

        assertThat(table.getCurrentTurn()).isEqualTo(2);
        assertThat(table.getRows()).hasSize(1);
        assertThat(table.getRows().get(0).getStockName()).isEqualTo("한빛반도체");
        assertThat(table.getRows().get(0).getPrices()).hasSize(12)
                .startsWith(10_000, 10_500, null);
    }

    @Test
    void 가격을_넣으면_새_칸을_만들고_변동률을_계산한다() {
        existingPrice(1, 10_000);

        PriceSaveResultDTO result = save(false, PriceCellDTO.ofPrice(1L, 2, 11_000));

        assertThat(result.getSaved()).isEqualTo(1);
        assertThat(result.getWarnings()).isEmpty();
        StockPrice row = insertedRows().get(0);
        assertThat(row.getTurnNo()).isEqualTo(2);
        assertThat(row.getPrice()).isEqualTo(11_000);
        assertThat(row.getChangeRate()).isEqualByComparingTo("10.00");
    }

    @Test
    void 변동률로_넣으면_지난_턴_가격에서_50원_단위로_계산한다() {
        existingPrice(1, 10_000);

        save(false, new PriceCellDTO(1L, 2, null, new BigDecimal("-3.33")));

        // 10,000 x (1 - 0.0333) = 9,667 -> 50원 단위 반올림 9,650
        assertThat(insertedRows().get(0).getPrice()).isEqualTo(9_650);
    }

    @Test
    void 지난_턴은_PAST_TURN_LOCKED() {
        currentTurn(5);

        assertThatThrownBy(() -> save(true, PriceCellDTO.ofPrice(1L, 5, 12_000)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.PAST_TURN_LOCKED);
    }

    @Test
    void 삼십퍼센트를_넘으면_경고만_주고_confirm_없이는_저장하지_않는다() {
        existingPrice(1, 10_000);

        PriceSaveResultDTO result = save(false, PriceCellDTO.ofPrice(1L, 2, 13_100));

        assertThat(result.getSaved()).isZero();
        assertThat(result.getWarnings()).hasSize(1);
        assertThat(result.getWarnings().get(0).getTurnNo()).isEqualTo(2);
        verify(priceRepository, never()).saveAll(anyList());
    }

    @Test
    void confirm이면_경고가_있어도_저장한다() {
        existingPrice(1, 10_000);

        PriceSaveResultDTO result = save(true, PriceCellDTO.ofPrice(1L, 2, 13_100));

        assertThat(result.getSaved()).isEqualTo(1);
        assertThat(result.getWarnings()).hasSize(1);
        assertThat(insertedRows()).hasSize(1);
    }

    @Test
    void 일턴은_시작_가격과_비교해서_경고한다() {
        PriceSaveResultDTO result = save(false, PriceCellDTO.ofPrice(1L, 1, 6_000));

        assertThat(result.getWarnings()).hasSize(1);
    }

    @Test
    void 영원_이하는_PRICE_NOT_POSITIVE() {
        assertThatThrownBy(() -> save(true, PriceCellDTO.ofPrice(1L, 1, 0)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.PRICE_NOT_POSITIVE);
    }

    @Test
    void 변동률로_계산한_가격이_int_범위를_넘으면_PRICE_OUT_OF_RANGE() {
        assertThatThrownBy(() -> PriceAdminService.priceFromRate(76_800, new BigDecimal("5000000")))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.PRICE_OUT_OF_RANGE);
    }

    @Test
    void 기존_칸을_바꾸면_다음_턴_변동률도_다시_계산한다() {
        existingPrice(1, 10_000);
        StockPrice turn2 = existingPrice(2, 10_000);
        StockPrice turn3 = existingPrice(3, 11_000);

        save(true, PriceCellDTO.ofPrice(1L, 2, 10_500));

        assertThat(turn2.getPrice()).isEqualTo(10_500);
        assertThat(turn2.getChangeRate()).isEqualByComparingTo("5.00");
        assertThat(turn3.getChangeRate()).isEqualByComparingTo("4.76");
    }

    @Test
    void CSV는_BOM_제목줄_종목_행으로_내려받는다() {
        existingPrice(1, 10_000);

        String csv = service.exportCsv(SEASON_ID);

        assertThat(csv).startsWith("﻿stockId,stockName,turn1,turn2,");
        assertThat(csv).contains("turn12\r\n");
        assertThat(csv).contains("1,한빛반도체,10000,,,,,,,,,,,\r\n");
    }

    @Test
    void CSV를_올리면_빈_칸은_두고_값이_있는_칸만_저장한다() throws Exception {
        // 내려받은 빈 가격표의 1·2턴 칸만 채운다 (칸 수는 그대로)
        String csv = service.exportCsv(SEASON_ID).replace("1,한빛반도체,,,", "1,한빛반도체,10000,10100,");

        PriceSaveResultDTO result = service.importCsv(SEASON_ID, stream(csv));

        assertThat(result.getSaved()).isEqualTo(2);
        assertThat(insertedRows()).extracting(StockPrice::getTurnNo).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void 이름에_쉼표_따옴표가_있어도_내려받은_CSV를_그대로_올릴_수_있다() throws Exception {
        when(marketAdminService.findStocks()).thenReturn(List.of(new AdminStockDTO(1L, 1L, "반도체",
                "한빛,\"반도체\"", 10_000, null, null, null, null, true)));
        String csv = service.exportCsv(SEASON_ID);
        assertThat(csv).contains("1,\"한빛,\"\"반도체\"\"\",,,");

        PriceSaveResultDTO result = service.importCsv(SEASON_ID,
                stream(csv.replace("\"\",,,", "\"\",10000,10100,")));

        assertThat(result.getSaved()).isEqualTo(2);
        assertThat(insertedRows()).extracting(StockPrice::getTurnNo).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void CSV_이름_칸은_수식으로_읽히지_않게_내려받는다() {
        assertThat(PriceAdminService.csvCell("=SUM(A1)")).isEqualTo("'=SUM(A1)");
        assertThat(PriceAdminService.csvCell("-1,000")).isEqualTo("\"'-1,000\"");
        assertThat(PriceAdminService.csvCell("한빛반도체")).isEqualTo("한빛반도체");
    }

    @Test
    void CSV에_오류_행이_있으면_전체를_저장하지_않고_오류_행을_알려준다() {
        currentTurn(1);
        existingPrice(1, 10_000);
        String csv = "stockId,stockName,turn1,turn2,turn3,turn4,turn5,turn6,turn7,turn8,turn9,turn10,turn11,turn12\n"
                + "1,한빛반도체,9999,abc,,,,,,,,,,\n"
                + "99,없는종목,,,,,,,,,,,,\n";

        assertThatThrownBy(() -> service.importCsv(SEASON_ID, stream(csv)))
                .isInstanceOf(CsvInvalidException.class)
                .satisfies(e -> assertThat(((CsvInvalidException) e).getErrors())
                        .extracting(CsvInvalidException.CsvError::line)
                        .containsExactly(2, 2, 3));
        verify(priceRepository, never()).saveAll(anyList());
    }

    @Test
    void CSV_제목줄이_다르면_CSV_INVALID() {
        assertThatThrownBy(() -> service.importCsv(SEASON_ID, stream("id,name\n1,x\n")))
                .isInstanceOf(CsvInvalidException.class)
                .extracting(e -> ((CsvInvalidException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.CSV_INVALID);
    }

    private static ByteArrayInputStream stream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }
}
