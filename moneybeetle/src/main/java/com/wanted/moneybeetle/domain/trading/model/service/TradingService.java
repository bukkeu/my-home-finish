package com.wanted.moneybeetle.domain.trading.model.service;

/* comment.
*   담당 : PM (모의투자)
*   홈, 시세, 종목 상세, 뉴스 조회 / 시즌 참가(캐시는 RewardService, 투자 가능 금액 확정 SP-52)
*   - #24 조회 (API 55~58) : 지금 보이는 시즌(진행 중·휴장 중·정산 중·종료)의 현재 턴까지만 보여 준다.
*     다음 턴 이후의 가격·뉴스는 미리 입력돼 있어도 응답에 넣지 않는다
*   - 사용 안 함 분야·종목은 목록과 상세에 나오지 않는다 (사용 안 함 분야의 종목 상세도 NOT_FOUND)
*   - #44·#193 관리자 : 다음 턴 점검(checkNextTurn), 대시보드 요약(getDashboardSummary, 역할분담 §7 연동 약속)
*  */

import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dao.HoldingRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.NewsRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.NewsTagRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SeasonParticipantRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SectorRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockPriceRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradeOrderRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.NewsDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.NextTurnCheckDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SectorStocksDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.StockDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.StockDetailDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.StockListDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TradingDashboardSummaryDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.Holding;
import com.wanted.moneybeetle.domain.trading.model.entity.News;
import com.wanted.moneybeetle.domain.trading.model.entity.NewsTag;
import com.wanted.moneybeetle.domain.trading.model.entity.Sector;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingService {

    /**
     * 사용자에게 보이는 시즌 상태 : 진행 중, 일요일 휴장 중, 정산 중, 종료.
     * 정산 중·종료에도 시세·뉴스를 계속 보여 준다. 정산 중에는 주문만 막힌다(SEASON_CLOSED, SP-58)
     * 준비 중(READY) 시즌은 보이지 않고, 새 시즌이 진행 중이 되면 시작일이 더 늦은 그 시즌이 보인다
     */
    public static final List<String> VISIBLE_STATUSES = List.of(TradingSeason.STATUS_RUNNING,
            TradingSeason.STATUS_HOLIDAY, TradingSeason.STATUS_SETTLING, TradingSeason.STATUS_ENDED);
    /** 시세 정렬 : 인기순 (이번 시즌 거래 수가 많은 순) */
    public static final String SORT_POPULAR = "popular";

    private final TradingSeasonRepository seasonRepository;
    private final SectorRepository sectorRepository;
    private final StockRepository stockRepository;
    private final StockPriceRepository priceRepository;
    private final NewsRepository newsRepository;
    private final NewsTagRepository newsTagRepository;
    private final SeasonParticipantRepository participantRepository;
    private final HoldingRepository holdingRepository;
    private final TradeOrderRepository orderRepository;
    private final Clock clock;

    /** API 55 분야 목록 : 사용 중 분야를 정렬 순서대로, 분야마다 사용 중 종목 */
    public List<SectorStocksDTO> findSectors() {
        Map<Long, List<Stock>> stocksBySector = stockRepository.findByIsActiveTrueOrderByStockIdAsc().stream()
                .collect(Collectors.groupingBy(Stock::getSectorId));
        return sectorRepository.findByIsActiveTrueOrderBySortOrderAscSectorIdAsc().stream()
                .map(sector -> SectorStocksDTO.of(sector, stocksBySector.getOrDefault(sector.getSectorId(), List.of())))
                .toList();
    }

    /**
     * API 56 오늘의 가상 시세 : 현재 턴 가격과 등락률
     * - 기본 정렬은 분야 정렬 순서 -> 종목 번호, sort=popular 면 이번 시즌 거래 수가 많은 순(같으면 종목 번호)
     * - sectorId, keyword(종목 이름 일부)로 거를 수 있다
     */
    public StockListDTO findStocks(String sort, Long sectorId, String keyword) {
        TradingSeason season = getVisibleSeason();
        List<Sector> sectors = sectorRepository.findByIsActiveTrueOrderBySortOrderAscSectorIdAsc();
        Map<Long, Sector> sectorById = sectors.stream()
                .collect(Collectors.toMap(Sector::getSectorId, Function.identity()));
        Map<Long, StockPrice> priceByStock = priceRepository
                .findByTradingSeasonIdAndTurnNo(season.getTradingSeasonId(), season.getCurrentTurn()).stream()
                .collect(Collectors.toMap(StockPrice::getStockId, Function.identity()));

        String word = keyword == null ? "" : keyword.trim();
        List<Stock> stocks = stockRepository.findByIsActiveTrueOrderByStockIdAsc().stream()
                .filter(stock -> sectorById.containsKey(stock.getSectorId()))
                .filter(stock -> sectorId == null || sectorId.equals(stock.getSectorId()))
                .filter(stock -> word.isEmpty() || stock.getName().contains(word))
                .sorted(stockOrder(sort, season, sectors))
                .toList();

        return StockListDTO.of(season.getCurrentTurn(), stocks.stream()
                .map(stock -> StockDTO.of(stock, sectorById.get(stock.getSectorId()).getName(),
                        priceByStock.get(stock.getStockId())))
                .toList());
    }

    /** API 57 종목 상세 : 1턴부터 현재 턴까지 가격 흐름, 기업정보, 내 보유(memberId 가 없으면 null) */
    public StockDetailDTO getStock(Long stockId, Long memberId) {
        TradingSeason season = getVisibleSeason();
        Stock stock = stockRepository.findById(stockId)
                .filter(Stock::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        // 사용 안 함 분야의 종목은 목록에 나오지 않으므로 상세도 NOT_FOUND
        String sectorName = sectorRepository.findById(stock.getSectorId())
                .filter(Sector::isActive)
                .map(Sector::getName)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        List<StockPrice> history = priceRepository.findByTradingSeasonIdAndStockIdAndTurnNoLessThanEqualOrderByTurnNoAsc(
                season.getTradingSeasonId(), stockId, season.getCurrentTurn());
        return StockDetailDTO.of(stock, sectorName, season.getCurrentTurn(), history,
                findHolding(season, stockId, memberId));
    }

    /**
     * API 58 가상 뉴스 : 현재 턴까지 공개된 뉴스, 최근 턴부터
     * - turnNo 를 주면 그 턴 뉴스만 (현재 턴보다 뒤면 아직 공개 전이라 빈 목록)
     * - stockId 를 주면 그 종목이 관련 종목으로 걸린 뉴스만 (종목 상세의 뉴스 탭)
     */
    public List<NewsDTO> findNews(Integer turnNo, Long stockId) {
        TradingSeason season = getVisibleSeason();
        if (turnNo != null && turnNo > season.getCurrentTurn()) {
            return List.of();
        }
        List<News> newsList = newsRepository.findByTradingSeasonIdAndTurnNoLessThanEqualOrderByTurnNoDescNewsIdDesc(
                        season.getTradingSeasonId(), season.getCurrentTurn()).stream()
                .filter(news -> turnNo == null || news.getTurnNo() == turnNo)
                .toList();
        if (newsList.isEmpty()) {
            return List.of();
        }

        Map<Long, List<NewsTag>> tagsByNews = newsTagRepository
                .findByNewsIdIn(newsList.stream().map(News::getNewsId).toList()).stream()
                .collect(Collectors.groupingBy(NewsTag::getNewsId));
        // 이름은 뉴스에 걸린 분야·종목만 조회한다
        List<NewsTag> allTags = tagsByNews.values().stream().flatMap(List::stream).toList();
        Map<Long, String> sectorNames = sectorRepository.findAllById(allTags.stream()
                        .filter(NewsTag::isSectorTag).map(NewsTag::getSectorId).distinct().toList()).stream()
                .collect(Collectors.toMap(Sector::getSectorId, Sector::getName));
        Map<Long, String> stockNames = stockRepository.findAllById(allTags.stream()
                        .filter(NewsTag::isStockTag).map(NewsTag::getStockId).distinct().toList()).stream()
                .collect(Collectors.toMap(Stock::getStockId, Stock::getName));

        return newsList.stream()
                .filter(news -> stockId == null || tagsByNews.getOrDefault(news.getNewsId(), List.of()).stream()
                        .anyMatch(tag -> stockId.equals(tag.getStockId())))
                .map(news -> {
                    List<NewsTag> tags = tagsByNews.getOrDefault(news.getNewsId(), List.of());
                    return NewsDTO.of(news,
                            tags.stream().filter(NewsTag::isSectorTag).map(tag -> sectorNames.get(tag.getSectorId()))
                                    .filter(Objects::nonNull).toList(),
                            tags.stream().filter(NewsTag::isStockTag).map(tag -> stockNames.get(tag.getStockId()))
                                    .filter(Objects::nonNull).toList());
                })
                .toList();
    }

    /** 지금 보이는 시즌. 보이는 상태의 시즌이 하나도 없으면(준비 중뿐이거나 시즌 없음) NO_RUNNING_SEASON (404) */
    private TradingSeason getVisibleSeason() {
        return seasonRepository.findFirstByStatusInOrderByStartDateDesc(VISIBLE_STATUSES)
                .orElseThrow(() -> new BusinessException(TradingErrorCode.NO_RUNNING_SEASON));
    }

    private Holding findHolding(TradingSeason season, Long stockId, Long memberId) {
        if (memberId == null) {
            return null;
        }
        return participantRepository.findByTradingSeasonIdAndMemberId(season.getTradingSeasonId(), memberId)
                .flatMap(participant -> holdingRepository.findBySeasonParticipantIdAndStockId(
                        participant.getSeasonParticipantId(), stockId))
                .orElse(null);
    }

    private Comparator<Stock> stockOrder(String sort, TradingSeason season, List<Sector> sectors) {
        if (SORT_POPULAR.equals(sort)) {
            Map<Long, Long> orderCounts = orderRepository.countOrdersByStock(season.getTradingSeasonId()).stream()
                    .collect(Collectors.toMap(TradeOrderRepository.StockOrderCount::getStockId,
                            TradeOrderRepository.StockOrderCount::getOrderCount));
            return Comparator.comparing((Stock stock) -> orderCounts.getOrDefault(stock.getStockId(), 0L))
                    .reversed()
                    .thenComparing(Stock::getStockId);
        }
        Map<Long, Integer> sectorRank = new HashMap<>();
        for (int i = 0; i < sectors.size(); i++) {
            sectorRank.put(sectors.get(i).getSectorId(), i);
        }
        return Comparator.comparing((Stock stock) -> sectorRank.get(stock.getSectorId()))
                .thenComparing(Stock::getStockId);
    }

    /**
     * 다음 턴 점검 (API 119, SP-59). 대시보드 요약(#193)도 이 계산을 그대로 쓴다.
     * 다음 턴이 있고, 휴장이 아니고, 진행 중이고, 사용 중 종목이 있고 그 가격이 모두 있고, 뉴스가 1개 이상 있어야 ready
     */
    public NextTurnCheckDTO checkNextTurn(TradingSeason season) {
        boolean holiday = season.isHoliday();
        if (season.isLastTurn()) {
            return new NextTurnCheckDTO(false, null, List.of(), 0, holiday, isPreviousSeasonSettled(season));
        }
        int nextTurn = season.getCurrentTurn() + 1;
        Set<Long> activeSectorIds = sectorRepository.findByIsActiveTrueOrderBySortOrderAscSectorIdAsc().stream()
                .map(Sector::getSectorId).collect(Collectors.toSet());
        List<Stock> stocks = stockRepository.findByIsActiveTrueOrderByStockIdAsc().stream()
                .filter(stock -> activeSectorIds.contains(stock.getSectorId()))
                .toList();
        Set<Long> pricedStockIds = priceRepository.findByTradingSeasonIdAndTurnNo(season.getTradingSeasonId(), nextTurn)
                .stream().map(StockPrice::getStockId).collect(Collectors.toSet());
        List<String> missingPrices = stocks.stream()
                .filter(stock -> !pricedStockIds.contains(stock.getStockId()))
                .map(Stock::getName)
                .toList();
        long newsCount = newsRepository.countByTradingSeasonIdAndTurnNo(season.getTradingSeasonId(), nextTurn);
        boolean previousSettled = isPreviousSeasonSettled(season);
        boolean ready = season.isRunning() && !stocks.isEmpty() && missingPrices.isEmpty() && newsCount > 0
                && previousSettled;
        return new NextTurnCheckDTO(ready, nextTurn, missingPrices, newsCount, holiday, previousSettled);
    }

    /** 이전 시즌(시작일이 더 이른 가장 최근 시즌)이 없거나, 정산을 마치고 끝났는가 (#207) */
    private boolean isPreviousSeasonSettled(TradingSeason season) {
        return seasonRepository.findFirstByStartDateBeforeOrderByStartDateDesc(season.getStartDate())
                .map(TradingSeason::isSettledAndEnded)
                .orElse(true);
    }

    /**
     * 관리자 대시보드(API 84)용 요약 (#193, 역할분담 §7 : 김예린 DashboardService 가 호출).
     * 진행 중·휴장 중인 시즌 하나의 오늘 주문 수와 다음 턴 점검 결과. 그런 시즌이 없으면 비어 있다
     */
    public Optional<TradingDashboardSummaryDTO> getDashboardSummary() {
        return seasonRepository.findFirstByStatusInOrderByStartDateDesc(
                        List.of(TradingSeason.STATUS_RUNNING, TradingSeason.STATUS_HOLIDAY))
                .map(season -> {
                    LocalDate today = LocalDate.now(clock);
                    long todayOrders = orderRepository.countOrdersBetween(season.getTradingSeasonId(),
                            today.atStartOfDay(), today.plusDays(1).atStartOfDay());
                    NextTurnCheckDTO check = checkNextTurn(season);
                    return new TradingDashboardSummaryDTO(season.getTradingSeasonId(), todayOrders, check.ready(),
                            new TradingDashboardSummaryDTO.Missing(check.missingPrices().size(),
                                    check.nextTurn() != null && check.newsCount() == 0 ? 1 : 0));
                });
    }
}
