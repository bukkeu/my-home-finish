package com.wanted.moneybeetle.domain.trading.model.service;

/* comment.
*   담당 : PM (모의투자)
*   다른 도메인(home 통합 검색, #15)이 부르는 종목 검색 창구 (역할분담 §7 연동 약속)
*   - searchStocks(keyword) : 사용 중인 분야의 사용 중인 종목 중 이름에 키워드가 들어간 것, 종목 번호 순
*   해금 레벨(Lv.8) 확인은 부르는 쪽(HomeService)이 한다
*  */

import com.wanted.moneybeetle.domain.trading.model.dao.SectorRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.StockSearchDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.Sector;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockSearchService {

    private final StockRepository stockRepository;
    private final SectorRepository sectorRepository;

    public List<StockSearchDTO> searchStocks(String keyword) {
        List<Stock> stocks = stockRepository.findByNameContainingAndIsActiveTrueOrderByStockIdAsc(keyword);
        if (stocks.isEmpty()) {
            return List.of();
        }
        Map<Long, Sector> sectors = sectorRepository.findAllById(stocks.stream().map(Stock::getSectorId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Sector::getSectorId, Function.identity()));
        return stocks.stream()
                .filter(stock -> sectors.containsKey(stock.getSectorId()) && sectors.get(stock.getSectorId()).isActive())
                .map(stock -> StockSearchDTO.of(stock.getStockId(), stock.getName(),
                        sectors.get(stock.getSectorId()).getName()))
                .toList();
    }
}
