package com.wanted.moneybeetle.domain.trading.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dao.SectorRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.AdminStockDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SectorSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.StockSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.Sector;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : PM (#41)
*   MarketAdminService 분야·종목 확인 (DB 없이 Repository 는 가짜(mock))
*   - 이름 중복 409(동시 요청으로 DB UNIQUE 가 막은 경우 포함), 분야당 사용 중 종목 2개, 없는 분야·종목 404,
*     수정 값 반영, 종목 목록 정렬(분야 순서)
*  */
class MarketAdminServiceTest {

    private SectorRepository sectorRepository;
    private StockRepository stockRepository;
    private com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository seasonRepository;
    private MarketAdminService service;

    @BeforeEach
    void setUp() {
        sectorRepository = mock(SectorRepository.class);
        stockRepository = mock(StockRepository.class);
        seasonRepository = org.mockito.Mockito.mock(com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository.class);
        service = new MarketAdminService(sectorRepository, stockRepository, seasonRepository);
    }

    private Sector sector(long id, String name, int sortOrder) {
        Sector sector = Sector.create(name, sortOrder);
        ReflectionTestUtils.setField(sector, "sectorId", id);
        return sector;
    }

    private Stock stock(long id, long sectorId, String name) {
        Stock stock = Stock.create(sectorId, name, 10_000);
        ReflectionTestUtils.setField(stock, "stockId", id);
        return stock;
    }

    @Test
    void 분야를_등록하면_번호를_돌려준다() {
        when(sectorRepository.existsByName("핀테크")).thenReturn(false);
        when(sectorRepository.saveAndFlush(any(Sector.class))).thenAnswer(invocation -> {
            Sector saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "sectorId", 15L);
            return saved;
        });

        assertThat(service.createSector(new SectorSaveDTO("핀테크", 15, null))).isEqualTo(15L);
    }

    @Test
    void 같은_이름의_분야는_SECTOR_DUPLICATED() {
        when(sectorRepository.existsByName("반도체")).thenReturn(true);

        assertThatThrownBy(() -> service.createSector(new SectorSaveDTO("반도체", 1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.SECTOR_DUPLICATED);
        verify(sectorRepository, never()).saveAndFlush(any());
    }

    @Test
    void 분야_수정은_이름과_사용여부를_바꾸고_순서는_줄_때만_바꾼다() {
        Sector sector = sector(1L, "반도체", 1);
        when(sectorRepository.findById(1L)).thenReturn(Optional.of(sector));

        service.changeSector(1L, new SectorSaveDTO("시스템반도체", null, false));

        assertThat(sector.getName()).isEqualTo("시스템반도체");
        assertThat(sector.getSortOrder()).isEqualTo(1);
        assertThat(sector.isActive()).isFalse();
    }

    @Test
    void 없는_분야를_고치면_NOT_FOUND() {
        when(sectorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeSector(99L, new SectorSaveDTO("x", null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 종목을_등록하면_기업정보까지_저장한다() {
        when(sectorRepository.findById(1L)).thenReturn(Optional.of(sector(1L, "반도체", 1)));
        when(stockRepository.existsByName("한빛반도체")).thenReturn(false);
        when(stockRepository.saveAndFlush(any(Stock.class))).thenAnswer(invocation -> {
            Stock saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "stockId", 1L);
            return saved;
        });

        Long id = service.createStock(new StockSaveDTO(1L, "한빛반도체", 76_800, "소개", "메모리",
                "1조 2,000억", "1,450억"));

        assertThat(id).isEqualTo(1L);
        verify(stockRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(stock ->
                stock.getStartPrice() == 76_800
                        && "메모리".equals(stock.getBusiness())
                        && "1,450억".equals(stock.getOperatingProfit())));
    }

    @Test
    void 없는_분야에_종목을_등록하면_NOT_FOUND() {
        when(sectorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createStock(new StockSaveDTO(99L, "새종목", 1_000, null, null, null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 같은_이름의_종목은_STOCK_DUPLICATED() {
        when(sectorRepository.findById(1L)).thenReturn(Optional.of(sector(1L, "반도체", 1)));
        when(stockRepository.existsByName("한빛반도체")).thenReturn(true);

        assertThatThrownBy(() -> service.createStock(new StockSaveDTO(1L, "한빛반도체", 1_000, null, null, null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.STOCK_DUPLICATED);
    }

    @Test
    void 종목_수정은_자기_이름은_중복으로_보지_않는다() {
        Stock stock = stock(1L, 1L, "한빛반도체");
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(sectorRepository.findById(2L)).thenReturn(Optional.of(sector(2L, "모빌리티", 2)));
        when(stockRepository.existsByNameAndStockIdNot("한빛반도체", 1L)).thenReturn(false);

        service.changeStock(1L, new StockSaveDTO(2L, "한빛반도체", 80_000, "새 소개", null, null, null));

        assertThat(stock.getSectorId()).isEqualTo(2L);
        assertThat(stock.getStartPrice()).isEqualTo(80_000);
        assertThat(stock.getIntro()).isEqualTo("새 소개");
    }

    @Test
    void 분야_수정은_다른_분야와_이름이_같으면_SECTOR_DUPLICATED() {
        Sector sector = sector(1L, "반도체", 1);
        when(sectorRepository.findById(1L)).thenReturn(Optional.of(sector));
        when(sectorRepository.existsByNameAndSectorIdNot("모빌리티", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.changeSector(1L, new SectorSaveDTO("모빌리티", null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.SECTOR_DUPLICATED);
        assertThat(sector.getName()).isEqualTo("반도체");
    }

    @Test
    void 종목_수정은_다른_종목과_이름이_같으면_STOCK_DUPLICATED() {
        Stock stock = stock(1L, 1L, "한빛반도체");
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(sectorRepository.findById(1L)).thenReturn(Optional.of(sector(1L, "반도체", 1)));
        when(stockRepository.existsByNameAndStockIdNot("모아모빌리티", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.changeStock(1L,
                new StockSaveDTO(1L, "모아모빌리티", 1_000, null, null, null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.STOCK_DUPLICATED);
        assertThat(stock.getName()).isEqualTo("한빛반도체");
    }

    @Test
    void 없는_종목을_고치면_NOT_FOUND() {
        when(stockRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStock(99L,
                new StockSaveDTO(1L, "새종목", 1_000, null, null, null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 종목을_없는_분야로_옮기면_NOT_FOUND() {
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock(1L, 1L, "한빛반도체")));
        when(sectorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStock(1L,
                new StockSaveDTO(99L, "한빛반도체", 1_000, null, null, null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 동시_등록으로_이름_UNIQUE가_막으면_STOCK_DUPLICATED() {
        when(sectorRepository.findById(1L)).thenReturn(Optional.of(sector(1L, "반도체", 1)));
        when(stockRepository.existsByName("한빛반도체")).thenReturn(false);
        when(stockRepository.saveAndFlush(any(Stock.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement", new RuntimeException("Duplicate entry '한빛반도체' for key 'stock.uk_stock_name'")));

        assertThatThrownBy(() -> service.createStock(new StockSaveDTO(1L, "한빛반도체", 1_000, null, null, null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.STOCK_DUPLICATED);
    }

    @Test
    void 동시_수정으로_이름_UNIQUE가_막으면_SECTOR_DUPLICATED() {
        when(sectorRepository.findById(1L)).thenReturn(Optional.of(sector(1L, "반도체", 1)));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException(
                "could not execute statement", new RuntimeException("Duplicate entry for key 'sector.UK_SECTOR_NAME'")))
                .when(sectorRepository).flush();

        assertThatThrownBy(() -> service.changeSector(1L, new SectorSaveDTO("모빌리티", null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.SECTOR_DUPLICATED);
    }

    @Test
    void 분야_동시_등록으로_이름_UNIQUE가_막으면_SECTOR_DUPLICATED() {
        when(sectorRepository.existsByName("핀테크")).thenReturn(false);
        when(sectorRepository.saveAndFlush(any(Sector.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement", new RuntimeException("Duplicate entry '핀테크' for key 'sector.uk_sector_name'")));

        assertThatThrownBy(() -> service.createSector(new SectorSaveDTO("핀테크", 15, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.SECTOR_DUPLICATED);
    }

    @Test
    void 종목_동시_수정으로_이름_UNIQUE가_막으면_STOCK_DUPLICATED() {
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock(1L, 1L, "한빛반도체")));
        when(sectorRepository.findById(1L)).thenReturn(Optional.of(sector(1L, "반도체", 1)));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException(
                "could not execute statement", new RuntimeException("Duplicate entry for key 'stock.uk_stock_name'")))
                .when(stockRepository).flush();

        assertThatThrownBy(() -> service.changeStock(1L,
                new StockSaveDTO(1L, "모아모빌리티", 1_000, null, null, null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.STOCK_DUPLICATED);
    }

    @Test
    void 이름_UNIQUE가_아닌_무결성_오류는_그대로_던진다() {
        when(sectorRepository.findById(1L)).thenReturn(Optional.of(sector(1L, "반도체", 1)));
        when(stockRepository.existsByName("새종목")).thenReturn(false);
        DataIntegrityViolationException other = new DataIntegrityViolationException(
                "could not execute statement", new RuntimeException("Cannot add or update a child row: fk_stock_sector"));
        when(stockRepository.saveAndFlush(any(Stock.class))).thenThrow(other);

        assertThatThrownBy(() -> service.createStock(new StockSaveDTO(1L, "새종목", 1_000, null, null, null, null)))
                .isSameAs(other);
    }

    @Test
    void 사용_중_종목이_2개인_분야에_등록하면_STOCK_LIMIT_EXCEEDED() {
        when(sectorRepository.findById(1L)).thenReturn(Optional.of(sector(1L, "반도체", 1)));
        when(stockRepository.existsByName("세번째반도체")).thenReturn(false);
        when(stockRepository.countBySectorIdAndIsActiveTrue(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.createStock(new StockSaveDTO(1L, "세번째반도체", 1_000, null, null, null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.STOCK_LIMIT_EXCEEDED);
        verify(stockRepository, never()).saveAndFlush(any());
    }

    @Test
    void 사용_중_종목을_2개인_분야로_옮기면_STOCK_LIMIT_EXCEEDED() {
        Stock stock = stock(1L, 1L, "한빛반도체");
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(sectorRepository.findById(2L)).thenReturn(Optional.of(sector(2L, "모빌리티", 2)));
        when(stockRepository.countBySectorIdAndIsActiveTrue(2L)).thenReturn(2L);

        assertThatThrownBy(() -> service.changeStock(1L,
                new StockSaveDTO(2L, "한빛반도체", 1_000, null, null, null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.STOCK_LIMIT_EXCEEDED);
        assertThat(stock.getSectorId()).isEqualTo(1L);
    }

    @Test
    void 같은_분야_안에서_고치거나_사용_안_함_종목을_옮길_때는_개수를_세지_않는다() {
        Stock active = stock(1L, 1L, "한빛반도체");
        Stock inactive = stock(2L, 1L, "옛반도체");
        inactive.deactivate();
        when(stockRepository.findById(1L)).thenReturn(Optional.of(active));
        when(stockRepository.findById(2L)).thenReturn(Optional.of(inactive));
        when(sectorRepository.findById(1L)).thenReturn(Optional.of(sector(1L, "반도체", 1)));
        when(sectorRepository.findById(2L)).thenReturn(Optional.of(sector(2L, "모빌리티", 2)));
        when(stockRepository.countBySectorIdAndIsActiveTrue(any())).thenReturn(2L);

        service.changeStock(1L, new StockSaveDTO(1L, "한빛반도체", 90_000, "새 소개", null, null, null));
        service.changeStock(2L, new StockSaveDTO(2L, "옛반도체", 1_000, null, null, null, null));

        assertThat(active.getStartPrice()).isEqualTo(90_000);
        assertThat(inactive.getSectorId()).isEqualTo(2L);
    }

    @Test
    void 종목_목록은_분야_정렬_순서대로_분야_이름을_붙인다() {
        when(sectorRepository.findAllByOrderBySortOrderAscSectorIdAsc())
                .thenReturn(List.of(sector(2L, "모빌리티", 1), sector(1L, "반도체", 2)));
        when(stockRepository.findAllByOrderByStockIdAsc())
                .thenReturn(List.of(stock(1L, 1L, "한빛반도체"), stock(2L, 2L, "모아모빌리티")));

        List<AdminStockDTO> stocks = service.findStocks();

        assertThat(stocks).extracting(AdminStockDTO::getName).containsExactly("모아모빌리티", "한빛반도체");
        assertThat(stocks).extracting(AdminStockDTO::getSectorName).containsExactly("모빌리티", "반도체");
    }

    // ===== 턴 진행과 같은 시즌 행 잠금 =====

    @Test
    void 분야_종목_변경은_READY_RUNNING_HOLIDAY_시즌을_직접_잠금_조회한다() {
        when(sectorRepository.existsByName("핀테크")).thenReturn(false);
        when(sectorRepository.saveAndFlush(any(Sector.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sectorRepository.findById(1L)).thenReturn(Optional.of(sector(1L, "반도체", 1)));
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock(1L, 1L, "한빛반도체")));
        when(stockRepository.saveAndFlush(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 네 변경 메서드가 각각 잠금을 잡는지 누적 횟수로 확인한다 (하나라도 빠지면 횟수가 어긋난다)
        service.createSector(new SectorSaveDTO("핀테크", 15, null));
        verify(seasonRepository, times(1)).findAllByStatusInForUpdate(List.of("READY", "RUNNING", "HOLIDAY"));

        service.changeSector(1L, new SectorSaveDTO("시스템반도체", null, null));
        verify(seasonRepository, times(2)).findAllByStatusInForUpdate(List.of("READY", "RUNNING", "HOLIDAY"));

        service.createStock(new StockSaveDTO(1L, "새반도체", 10_000, null, null, null, null));
        verify(seasonRepository, times(3)).findAllByStatusInForUpdate(List.of("READY", "RUNNING", "HOLIDAY"));

        service.changeStock(1L, new StockSaveDTO(1L, "한빛반도체", 10_000, null, null, null, null));
        verify(seasonRepository, times(4)).findAllByStatusInForUpdate(List.of("READY", "RUNNING", "HOLIDAY"));
    }
}
