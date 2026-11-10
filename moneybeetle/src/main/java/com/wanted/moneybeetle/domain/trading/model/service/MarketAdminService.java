package com.wanted.moneybeetle.domain.trading.model.service;

/* comment.
*   담당 : PM (모의투자)
*   관리자 시장 관리
*   - #41 분야·종목·기업정보 (API 121~126) : 이름 중복 금지(DB UNIQUE 도 함께), 없는 분야·종목은 404
*     같은 이름이 동시에 들어와 중복 확인을 둘 다 통과하면 DB UNIQUE 가 막는다 -> 바로 flush 해서 409 로 바꾼다
*     분야당 사용 중 종목은 2개까지 (ADM-17) : 등록·다른 분야로 옮기기에서 확인. 사용 안 함 종목은 세지 않는다
*   - 가격표(#42 : 지난 턴 잠금, ±30% 경고, CSV)·뉴스(#43)는 이 서비스에 이어서 추가한다
*  */

import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dao.SectorRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.AdminStockDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SectorDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SectorSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.StockSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.Sector;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketAdminService {

    /** schema.sql 의 이름 UNIQUE 제약 이름 */
    private static final String SECTOR_NAME_KEY = "uk_sector_name";
    private static final String STOCK_NAME_KEY = "uk_stock_name";
    /** 분야당 사용 중 종목 수 (ADM-17 : 분야별 2개씩 총 30개) */
    static final int MAX_ACTIVE_STOCKS_PER_SECTOR = 2;

    private final SectorRepository sectorRepository;
    private final StockRepository stockRepository;
    private final TradingSeasonRepository seasonRepository;

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
        lockActiveSeasons();
        if (sectorRepository.existsByName(request.getName())) {
            throw new BusinessException(TradingErrorCode.SECTOR_DUPLICATED);
        }
        int sortOrder = request.getSortOrder() == null ? 0 : request.getSortOrder();
        try {
            return sectorRepository.saveAndFlush(Sector.create(request.getName(), sortOrder)).getSectorId();
        } catch (DataIntegrityViolationException e) {
            throw duplicatedOrRethrow(e, SECTOR_NAME_KEY, TradingErrorCode.SECTOR_DUPLICATED);
        }
    }

    /** API 123 분야 수정 : 이름, 사용 여부(주면), 정렬 순서(주면) */
    @Transactional
    public void changeSector(Long sectorId, SectorSaveDTO request) {
        lockActiveSeasons();
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
        try {
            sectorRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw duplicatedOrRethrow(e, SECTOR_NAME_KEY, TradingErrorCode.SECTOR_DUPLICATED);
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
        lockActiveSeasons();
        getSector(request.getSectorId());
        if (stockRepository.existsByName(request.getName())) {
            throw new BusinessException(TradingErrorCode.STOCK_DUPLICATED);
        }
        checkStockLimit(request.getSectorId());
        Stock stock = Stock.create(request.getSectorId(), request.getName(), request.getStartPrice());
        stock.changeCompanyInfo(request.getIntro(), request.getBusiness(), request.getRevenue(),
                request.getOperatingProfit());
        try {
            return stockRepository.saveAndFlush(stock).getStockId();
        } catch (DataIntegrityViolationException e) {
            throw duplicatedOrRethrow(e, STOCK_NAME_KEY, TradingErrorCode.STOCK_DUPLICATED);
        }
    }

    /** API 126 종목·기업정보 수정 */
    @Transactional
    public void changeStock(Long stockId, StockSaveDTO request) {
        lockActiveSeasons();
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        getSector(request.getSectorId());
        if (stockRepository.existsByNameAndStockIdNot(request.getName(), stockId)) {
            throw new BusinessException(TradingErrorCode.STOCK_DUPLICATED);
        }
        // 같은 분야 안에서 이름·기업정보만 고칠 때는 세지 않는다. 사용 안 함 종목은 옮겨도 개수에 들어가지 않는다
        if (stock.isActive() && !stock.getSectorId().equals(request.getSectorId())) {
            checkStockLimit(request.getSectorId());
        }
        stock.changeBasicInfo(request.getSectorId(), request.getName(), request.getStartPrice());
        stock.changeCompanyInfo(request.getIntro(), request.getBusiness(), request.getRevenue(),
                request.getOperatingProfit());
        try {
            stockRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw duplicatedOrRethrow(e, STOCK_NAME_KEY, TradingErrorCode.STOCK_DUPLICATED);
        }
    }

    /**
     * 분야·종목 변경은 턴 진행(TurnAdminService.advanceTurn)과 같은 시즌 행 잠금을 잡고 시작한다.
     * 점검 직후 분야 활성화·종목 등록·이동이 끼어들어 가격이 없는 종목을 둔 채 턴이 넘어가지 않게 한다.
     * 준비·진행·휴장 시즌을 한 번에 잠금 조회해 READY → RUNNING 전환과 조회-잠금 사이의 틈도 막는다.
     * 대상 시즌이 없으면 잠글 행이 없으므로 그대로 진행한다
     */
    private void lockActiveSeasons() {
        seasonRepository.findAllByStatusInForUpdate(List.of(TradingSeason.STATUS_READY,
                TradingSeason.STATUS_RUNNING, TradingSeason.STATUS_HOLIDAY));
    }

    private Sector getSector(Long sectorId) {
        return sectorRepository.findById(sectorId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private void checkStockLimit(Long sectorId) {
        if (stockRepository.countBySectorIdAndIsActiveTrue(sectorId) >= MAX_ACTIVE_STOCKS_PER_SECTOR) {
            throw new BusinessException(TradingErrorCode.STOCK_LIMIT_EXCEEDED);
        }
    }

    /** 이름 UNIQUE 위반이면 중복 에러(409)로 바꾸고, 다른 무결성 오류는 그대로 던진다 */
    private static RuntimeException duplicatedOrRethrow(DataIntegrityViolationException e, String uniqueKey,
            TradingErrorCode duplicated) {
        String cause = e.getMostSpecificCause().getMessage();
        if (cause != null && cause.toLowerCase().contains(uniqueKey)) {
            return new BusinessException(duplicated);
        }
        return e;
    }

    private static String sectorNameOf(Map<Long, Sector> sectorById, Long sectorId) {
        Sector sector = sectorById.get(sectorId);
        return sector == null ? null : sector.getName();
    }
}
