package com.wanted.moneybeetle.domain.trading.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.trading.model.dao.SectorRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.StockSearchDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.Sector;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : PM (#15)
*   StockSearchService 확인 : 이름에 키워드가 들어간 사용 중 종목만, 사용 안 함 분야의 종목은 빼고 분야 이름을 붙인다
*  */
class StockSearchServiceTest {

    private final StockRepository stockRepository = mock(StockRepository.class);
    private final SectorRepository sectorRepository = mock(SectorRepository.class);
    private final StockSearchService service = new StockSearchService(stockRepository, sectorRepository);

    private Sector sector(long id, String name, boolean active) {
        Sector sector = Sector.create(name, (int) id);
        ReflectionTestUtils.setField(sector, "sectorId", id);
        if (!active) {
            sector.deactivate();
        }
        return sector;
    }

    private Stock stock(long id, long sectorId, String name) {
        Stock stock = Stock.create(sectorId, name, 10_000);
        ReflectionTestUtils.setField(stock, "stockId", id);
        return stock;
    }

    @Test
    void 사용_중_분야의_종목만_분야_이름과_함께_돌려준다() {
        when(stockRepository.findByNameContainingAndIsActiveTrueOrderByStockIdAsc("반도체"))
                .thenReturn(List.of(stock(1L, 1L, "한빛반도체"), stock(9L, 9L, "옛반도체")));
        when(sectorRepository.findAllById(anyList()))
                .thenReturn(List.of(sector(1L, "반도체", true), sector(9L, "옛분야", false)));

        List<StockSearchDTO> result = service.searchStocks("반도체");

        assertThat(result).extracting(StockSearchDTO::getName).containsExactly("한빛반도체");
        assertThat(result.get(0).getSectorName()).isEqualTo("반도체");
    }

    @Test
    void 찾은_종목이_없으면_분야를_조회하지_않고_빈_목록() {
        when(stockRepository.findByNameContainingAndIsActiveTrueOrderByStockIdAsc("없음")).thenReturn(List.of());

        assertThat(service.searchStocks("없음")).isEmpty();
    }
}
