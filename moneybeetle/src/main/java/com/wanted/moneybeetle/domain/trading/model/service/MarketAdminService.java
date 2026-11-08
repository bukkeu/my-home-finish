package com.wanted.moneybeetle.domain.trading.model.service;

/* comment.
*   담당 : PM 이재성 (모의투자)
*   관리자 시장 관리
*   - #41 분야·종목·기업정보 (API 121~126) : 이름 중복 금지(DB UNIQUE 도 함께), 없는 분야·종목은 404
*   - 가격표(#42 : 지난 턴 잠금, ±30% 경고, CSV)·뉴스(#43)는 이 서비스에 이어서 추가한다
*  */

import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dao.SectorRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.AdminStockDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SectorDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SectorSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.StockSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.Sector;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.util.Comparator;
import java.util.HashMap;
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
public class MarketAdminService {

    private final SectorRepository sectorRepository;
    private final StockRepository stockRepository;

    // ---------- 분야 ----------

    /** API 121 분야 목록 (정렬 순서대로, 사용 안 함 포함) */
    public List<SectorDTO> findSectors() {
        return sectorRepository.findAllByOrderBySortOrderAscSectorIdAsc().stream()
                .map(SectorDTO::from)
                .toList();
    }

    /** API 122 분야 등록 */
    @Transactional
    public Long createSector(SectorSaveDTO request) {
        if (sectorRepository.existsByName(request.getName())) {
            throw new BusinessException(TradingErrorCode.SECTOR_DUPLICATED);
        }
        int sortOrder = request.getSortOrder() == null ? 0 : request.getSortOrder();
        return sectorRepository.save(Sector.create(request.getName(), sortOrder)).getSectorId();
    }

    /** API 123 분야 수정 : 이름, 사용 여부(주면), 정렬 순서(주면) */
    @Transactional
    public void changeSector(Long sectorId, SectorSaveDTO request) {
        Sector sector = getSector(sectorId);
        if (sectorRepository.existsByNameAndSectorIdNot(request.getName(), sectorId)) {
            throw new BusinessException(TradingErrorCode.SECTOR_DUPLICATED);
        }
        sector.changeName(request.getName());
        if (request.getSortOrder() != null) {
            sector.changeSortOrder(request.getSortOrder());
        }
        if (Boolean.TRUE.equals(request.getIsActive())) {
            sector.activate();
        } else if (Boolean.FALSE.equals(request.getIsActive())) {
            sector.deactivate();
        }
    }

    // ---------- 종목 ----------

    /** API 124 종목 목록 (분야 정렬 순서 -> 종목 번호 순) */
    public List<AdminStockDTO> findStocks() {
        List<Sector> sectors = sectorRepository.findAllByOrderBySortOrderAscSectorIdAsc();
        Map<Long, Sector> sectorById = sectors.stream()
                .collect(Collectors.toMap(Sector::getSectorId, Function.identity()));
        Map<Long, Integer> sectorRank = new HashMap<>();
        for (int i = 0; i < sectors.size(); i++) {
            sectorRank.put(sectors.get(i).getSectorId(), i);
        }
        return stockRepository.findAllByOrderByStockIdAsc().stream()
                .sorted(Comparator.comparing(
                        (Stock stock) -> sectorRank.getOrDefault(stock.getSectorId(), Integer.MAX_VALUE)))
                .map(stock -> AdminStockDTO.from(stock, sectorNameOf(sectorById, stock.getSectorId())))
                .toList();
    }

    /** API 125 종목 등록 (기업정보 포함) */
    @Transactional
    public Long createStock(StockSaveDTO request) {
        getSector(request.getSectorId());
        if (stockRepository.existsByName(request.getName())) {
            throw new BusinessException(TradingErrorCode.STOCK_DUPLICATED);
        }
        Stock stock = Stock.create(request.getSectorId(), request.getName(), request.getStartPrice());
        stock.changeCompanyInfo(request.getIntro(), request.getBusiness(), request.getRevenue(),
                request.getOperatingProfit());
        return stockRepository.save(stock).getStockId();
    }

    /** API 126 종목·기업정보 수정 */
    @Transactional
    public void changeStock(Long stockId, StockSaveDTO request) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        getSector(request.getSectorId());
        if (stockRepository.existsByNameAndStockIdNot(request.getName(), stockId)) {
            throw new BusinessException(TradingErrorCode.STOCK_DUPLICATED);
        }
        stock.changeBasicInfo(request.getSectorId(), request.getName(), request.getStartPrice());
        stock.changeCompanyInfo(request.getIntro(), request.getBusiness(), request.getRevenue(),
                request.getOperatingProfit());
    }

    private Sector getSector(Long sectorId) {
        return sectorRepository.findById(sectorId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private static String sectorNameOf(Map<Long, Sector> sectorById, Long sectorId) {
        Sector sector = sectorById.get(sectorId);
        return sector == null ? null : sector.getName();
    }
}
