package com.wanted.moneybeetle.domain.trading.model.service;

/* comment.
*   담당 : PM 이재성 (모의투자)
*   관리자 턴별 가격표 (#42, API 127~130, ADM-19~24, SP-60)
*   - 지난 턴 잠금 : 현재 턴 이하(이미 거래에 쓰인 가격)는 바꿀 수 없다 -> PAST_TURN_LOCKED
*   - 변동률 입력 : 지난 턴 가격 x (1 + 변동률%) 를 50원 단위로 반올림 (지난 턴 가격이 없으면 시작 가격 기준)
*   - 이상값 : 지난 턴보다 ±30% 넘게 움직이면 경고. confirm=false 면 저장하지 않고 경고만 돌려준다
*   - 0원 이하 : DB 가 1원 이상만 받으므로(ck_stock_price_1) 경고가 아니라 거절 -> PRICE_NOT_POSITIVE
*   - 변동률(change_rate)은 저장할 때 지난 턴 가격으로 다시 계산한다 (1턴은 null)
*   - CSV : 제목 줄 stockId,stockName,turn1..turn12. 빈 칸은 그대로 두고, 오류 행이 하나라도 있으면 전체 취소
*  */

import com.wanted.moneybeetle.domain.trading.exception.CsvInvalidException;
import com.wanted.moneybeetle.domain.trading.exception.CsvInvalidException.CsvError;
import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dao.StockPriceRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.PriceCellDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.PriceSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.PriceSaveResultDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.PriceTableDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceAdminService {

    static final BigDecimal WARNING_RATE = new BigDecimal("30");
    static final String WARNING_MESSAGE = "한 턴에 ±30%를 넘는 변동이에요";
    private static final String UTF8_BOM = "﻿";

    private final TradingSeasonRepository seasonRepository;
    private final StockRepository stockRepository;
    private final StockPriceRepository priceRepository;
    private final MarketAdminService marketAdminService;

    /** API 127 턴별 가격표 */
    public PriceTableDTO findPriceTable(Long seasonId) {
        TradingSeason season = getSeason(seasonId);
        Map<Long, Map<Integer, StockPrice>> prices = pricesByStock(seasonId);
        List<PriceTableDTO.Row> rows = marketAdminService.findStocks().stream()
                .map(stock -> new PriceTableDTO.Row(stock.getStockId(), stock.getName(),
                        priceList(prices.get(stock.getStockId()), season.getTotalTurns())))
                .toList();
        return new PriceTableDTO(season.getCurrentTurn(), rows);
    }

    /** API 128 턴별 가격 저장 */
    @Transactional
    public PriceSaveResultDTO savePrices(Long seasonId, PriceSaveDTO request) {
        return apply(getSeason(seasonId), request.getCells(), request.isConfirm());
    }

    /** API 129 가격표 CSV 내려받기 (엑셀에서 한글이 깨지지 않게 BOM 을 붙인다) */
    public String exportCsv(Long seasonId) {
        TradingSeason season = getSeason(seasonId);
        PriceTableDTO table = findPriceTable(seasonId);
        StringBuilder csv = new StringBuilder(UTF8_BOM).append(header(season.getTotalTurns())).append("\r\n");
        for (PriceTableDTO.Row row : table.getRows()) {
            csv.append(row.getStockId()).append(',').append(row.getStockName());
            for (Integer price : row.getPrices()) {
                csv.append(',').append(price == null ? "" : price);
            }
            csv.append("\r\n");
        }
        return csv.toString();
    }

    /** API 130 가격표 CSV 올리기 : 형식 오류가 하나라도 있으면 전체 취소 */
    @Transactional
    public PriceSaveResultDTO importCsv(Long seasonId, InputStream csv) throws IOException {
        TradingSeason season = getSeason(seasonId);
        int totalTurns = season.getTotalTurns();
        Set<Long> stockIds = stockRepository.findAll().stream().map(Stock::getStockId).collect(Collectors.toSet());
        Map<Long, Map<Integer, StockPrice>> existing = pricesByStock(seasonId);

        List<String> lines = readLines(csv);
        List<CsvError> errors = new ArrayList<>();
        List<PriceCellDTO> cells = new ArrayList<>();
        if (lines.isEmpty() || !lines.get(0).trim().equals(header(totalTurns))) {
            errors.add(new CsvError(1, "제목 줄은 " + header(totalTurns) + " 이어야 해요"));
            throw new CsvInvalidException(errors);
        }
        Set<Long> seen = new HashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            int lineNo = i + 1;
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            String[] values = line.split(",", -1);
            if (values.length != totalTurns + 2) {
                errors.add(new CsvError(lineNo, "칸 수가 " + (totalTurns + 2) + "개여야 해요"));
                continue;
            }
            Long stockId = parseLong(values[0].trim());
            if (stockId == null || !stockIds.contains(stockId)) {
                errors.add(new CsvError(lineNo, "없는 종목 번호예요: " + values[0].trim()));
                continue;
            }
            if (!seen.add(stockId)) {
                errors.add(new CsvError(lineNo, "같은 종목이 두 번 나와요: " + stockId));
                continue;
            }
            for (int turn = 1; turn <= totalTurns; turn++) {
                String value = values[turn + 1].trim();
                if (value.isEmpty()) {
                    continue;
                }
                Long price = parseLong(value);
                if (price == null || price <= 0 || price > Integer.MAX_VALUE) {
                    errors.add(new CsvError(lineNo, turn + "턴 가격은 1 이상의 정수여야 해요: " + value));
                    continue;
                }
                if (turn <= season.getCurrentTurn()) {
                    // 지난 턴은 내려받은 값 그대로면 통과, 바뀌었으면 오류
                    StockPrice saved = existing.getOrDefault(stockId, Map.of()).get(turn);
                    if (saved == null || saved.getPrice() != price) {
                        errors.add(new CsvError(lineNo, turn + "턴은 지난 턴이라 바꿀 수 없어요"));
                    }
                    continue;
                }
                cells.add(PriceCellDTO.ofPrice(stockId, turn, price.intValue()));
            }
        }
        if (!errors.isEmpty()) {
            throw new CsvInvalidException(errors);
        }
        return apply(season, cells, true);
    }

    /** 가격 칸들을 검사하고, 경고가 없거나 confirm 이면 저장한다 */
    private PriceSaveResultDTO apply(TradingSeason season, List<PriceCellDTO> cells, boolean confirm) {
        Long seasonId = season.getTradingSeasonId();
        Map<Long, Stock> stocks = stockRepository.findAllById(
                        cells.stream().map(PriceCellDTO::getStockId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Stock::getStockId, Function.identity()));
        Map<Long, Map<Integer, StockPrice>> entities = pricesByStock(seasonId);

        // 계산용 가격표 (종목 -> 턴 -> 가격) : 같은 요청 안의 앞 턴 입력도 다음 턴 변동률 계산에 쓴다
        Map<Long, Map<Integer, Integer>> working = new HashMap<>();
        entities.forEach((stockId, byTurn) -> {
            Map<Integer, Integer> copy = new HashMap<>();
            byTurn.forEach((turn, price) -> copy.put(turn, price.getPrice()));
            working.put(stockId, copy);
        });

        List<PriceCellDTO> sorted = cells.stream()
                .sorted(Comparator.comparing(PriceCellDTO::getStockId).thenComparingInt(PriceCellDTO::getTurnNo))
                .toList();
        List<PriceSaveResultDTO.Warning> warnings = new ArrayList<>();
        Map<Long, Map<Integer, Integer>> resolved = new HashMap<>();
        for (PriceCellDTO cell : sorted) {
            Stock stock = stocks.get(cell.getStockId());
            if (stock == null) {
                throw new BusinessException(CommonErrorCode.NOT_FOUND);
            }
            if (cell.getTurnNo() <= season.getCurrentTurn()) {
                throw new BusinessException(TradingErrorCode.PAST_TURN_LOCKED);
            }
            Map<Integer, Integer> stockPrices = working.computeIfAbsent(stock.getStockId(), k -> new HashMap<>());
            Integer previous = previousPrice(stockPrices, cell.getTurnNo());
            int base = previous != null ? previous : stock.getStartPrice();
            int price = cell.getPrice() != null ? cell.getPrice() : priceFromRate(base, cell.getChangeRate());
            if (price <= 0) {
                throw new BusinessException(TradingErrorCode.PRICE_NOT_POSITIVE);
            }
            Integer lastTurnPrice = cell.getTurnNo() == 1 ? (Integer) stock.getStartPrice()
                    : stockPrices.get(cell.getTurnNo() - 1);
            if (lastTurnPrice != null && isBigMove(lastTurnPrice, price)) {
                warnings.add(new PriceSaveResultDTO.Warning(stock.getStockId(), cell.getTurnNo(), WARNING_MESSAGE));
            }
            stockPrices.put(cell.getTurnNo(), price);
            resolved.computeIfAbsent(stock.getStockId(), k -> new HashMap<>()).put(cell.getTurnNo(), price);
        }

        if (!warnings.isEmpty() && !confirm) {
            return new PriceSaveResultDTO(0, warnings);
        }

        List<StockPrice> newRows = new ArrayList<>();
        int saved = 0;
        for (Map.Entry<Long, Map<Integer, Integer>> entry : resolved.entrySet()) {
            Long stockId = entry.getKey();
            Map<Integer, StockPrice> byTurn = entities.computeIfAbsent(stockId, k -> new HashMap<>());
            for (Map.Entry<Integer, Integer> cell : entry.getValue().entrySet()) {
                StockPrice row = byTurn.get(cell.getKey());
                if (row == null) {
                    row = StockPrice.create(seasonId, stockId, cell.getKey(), cell.getValue(), null);
                    byTurn.put(cell.getKey(), row);
                    newRows.add(row);
                } else {
                    row.changePrice(cell.getValue(), row.getChangeRate());
                }
                saved++;
            }
            refreshChangeRates(byTurn, season);
        }
        priceRepository.saveAll(newRows);
        return new PriceSaveResultDTO(saved, warnings);
    }

    /** 잠기지 않은 턴의 변동률을 지난 턴 가격 기준으로 다시 계산 (1턴·지난 턴 가격이 없으면 null) */
    private void refreshChangeRates(Map<Integer, StockPrice> byTurn, TradingSeason season) {
        for (int turn = season.getCurrentTurn() + 1; turn <= season.getTotalTurns(); turn++) {
            StockPrice row = byTurn.get(turn);
            if (row == null) {
                continue;
            }
            StockPrice before = byTurn.get(turn - 1);
            BigDecimal rate = (turn == 1 || before == null) ? null : changeRate(before.getPrice(), row.getPrice());
            row.changePrice(row.getPrice(), rate);
        }
    }

    static int priceFromRate(int base, BigDecimal changeRate) {
        BigDecimal raw = BigDecimal.valueOf(base)
                .multiply(BigDecimal.ONE.add(changeRate.movePointLeft(2)));
        return raw.divide(BigDecimal.valueOf(50), 0, RoundingMode.HALF_UP).intValue() * 50;
    }

    static BigDecimal changeRate(int before, int after) {
        return BigDecimal.valueOf((long) after - before)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(before), 2, RoundingMode.HALF_UP);
    }

    static boolean isBigMove(int before, int after) {
        return changeRate(before, after).abs().compareTo(WARNING_RATE) > 0;
    }

    private static Integer previousPrice(Map<Integer, Integer> stockPrices, int turnNo) {
        for (int turn = turnNo - 1; turn >= 1; turn--) {
            Integer price = stockPrices.get(turn);
            if (price != null) {
                return price;
            }
        }
        return null;
    }

    private Map<Long, Map<Integer, StockPrice>> pricesByStock(Long seasonId) {
        Map<Long, Map<Integer, StockPrice>> result = new HashMap<>();
        for (StockPrice price : priceRepository.findAllByTradingSeasonId(seasonId)) {
            result.computeIfAbsent(price.getStockId(), k -> new HashMap<>()).put(price.getTurnNo(), price);
        }
        return result;
    }

    private static List<Integer> priceList(Map<Integer, StockPrice> byTurn, int totalTurns) {
        List<Integer> prices = new ArrayList<>();
        for (int turn = 1; turn <= totalTurns; turn++) {
            StockPrice price = byTurn == null ? null : byTurn.get(turn);
            prices.add(price == null ? null : price.getPrice());
        }
        return prices;
    }

    private static String header(int totalTurns) {
        StringBuilder header = new StringBuilder("stockId,stockName");
        for (int turn = 1; turn <= totalTurns; turn++) {
            header.append(",turn").append(turn);
        }
        return header.toString();
    }

    private static List<String> readLines(InputStream csv) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().collect(Collectors.toCollection(ArrayList::new));
            if (!lines.isEmpty() && lines.get(0).startsWith(UTF8_BOM)) {
                lines.set(0, lines.get(0).substring(1));
            }
            return lines;
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private TradingSeason getSeason(Long seasonId) {
        return seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
