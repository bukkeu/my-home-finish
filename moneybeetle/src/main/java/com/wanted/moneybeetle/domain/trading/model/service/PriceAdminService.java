package com.wanted.moneybeetle.domain.trading.model.service;

/* comment.
*   담당 : PM (모의투자)
*   관리자 턴별 가격표 (#42, API 127~130, ADM-19~24, SP-60)
*   - 지난 턴 잠금 : 현재 턴 이하(이미 거래에 쓰인 가격)는 바꿀 수 없다 -> PAST_TURN_LOCKED
*   - 변동률 입력 : 지난 턴 가격 x (1 + 변동률%) 를 50원 단위로 반올림 (지난 턴 가격이 없으면 시작 가격 기준)
*   - 이상값 : 지난 턴보다 ±30% 넘게 움직이면 경고. confirm=false 면 저장하지 않고 경고만 돌려준다
*   - 0원 이하 : DB 가 1원 이상만 받으므로(ck_stock_price_1) 경고가 아니라 거절 -> PRICE_NOT_POSITIVE
*   - 변동률로 계산한 가격이 int 범위를 넘으면 잘린 값이 저장되지 않게 거절 -> PRICE_OUT_OF_RANGE
*   - 변동률(change_rate)은 저장할 때 지난 턴 가격으로 다시 계산한다 (1턴은 null)
*   - CSV : 제목 줄 stockId,stockName,turn1..turn12. 빈 칸은 그대로 두고, 오류 행이 하나라도 있으면 전체 취소
*     종목 이름에 쉼표·따옴표·줄바꿈이 있어도 다시 올릴 수 있게 RFC 4180 방식으로 감싸고 읽는다
*     이름이 = + - @ 로 시작하면 엑셀이 수식으로 읽지 않게 앞에 ' 를 붙인다 (올릴 때 이름 칸은 쓰지 않는다)
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
import java.io.IOException;
import java.io.InputStream;
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
    /** 가격은 50원 단위 */
    private static final BigDecimal PRICE_UNIT = BigDecimal.valueOf(50);
    /** 엑셀이 수식으로 읽는 첫 글자 */
    private static final String FORMULA_START = "=+-@\t\r";

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
            csv.append(row.getStockId()).append(',').append(csvCell(row.getStockName()));
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

        List<CsvRow> rows = readRows(csv);
        List<CsvError> errors = new ArrayList<>();
        List<PriceCellDTO> cells = new ArrayList<>();
        if (rows.isEmpty() || !String.join(",", rows.get(0).values()).trim().equals(header(totalTurns))) {
            errors.add(new CsvError(1, "제목 줄은 " + header(totalTurns) + " 이어야 해요"));
            throw new CsvInvalidException(errors);
        }
        Set<Long> seen = new HashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            int lineNo = rows.get(i).line();
            List<String> row = rows.get(i).values();
            if (row.size() == 1 && row.get(0).isBlank()) {
                continue;
            }
            String[] values = row.toArray(String[]::new);
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
        BigDecimal rounded = raw.divide(PRICE_UNIT, 0, RoundingMode.HALF_UP).multiply(PRICE_UNIT);
        try {
            return rounded.intValueExact();
        } catch (ArithmeticException e) {
            // intValue() 는 넘치는 앞자리를 말없이 잘라서 엉뚱한 가격이 될 수 있다
            throw new BusinessException(TradingErrorCode.PRICE_OUT_OF_RANGE);
        }
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

    /** 이름 칸 : 수식으로 읽히는 첫 글자 앞에 ' 를 붙이고, 쉼표·따옴표·줄바꿈이 있으면 큰따옴표로 감싼다 (안쪽 " 는 "") */
    static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String safe = !value.isEmpty() && FORMULA_START.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    /** CSV 한 행 : 시작 줄 번호(오류 안내용)와 칸 값들 */
    private record CsvRow(int line, List<String> values) {
    }

    /** RFC 4180 방식으로 읽는다 : 큰따옴표 안의 쉼표·줄바꿈은 값, "" 는 따옴표 하나 */
    private static List<CsvRow> readRows(InputStream csv) throws IOException {
        String text = new String(csv.readAllBytes(), StandardCharsets.UTF_8);
        if (text.startsWith(UTF8_BOM)) {
            text = text.substring(1);
        }
        List<CsvRow> rows = new ArrayList<>();
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int rowLine = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    value.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                values.add(value.toString());
                value.setLength(0);
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                values.add(value.toString());
                value.setLength(0);
                rows.add(new CsvRow(rowLine, values));
                values = new ArrayList<>();
                line++;
                rowLine = line;
            } else {
                value.append(c);
            }
        }
        if (!values.isEmpty() || value.length() > 0) {
            values.add(value.toString());
            rows.add(new CsvRow(rowLine, values));
        }
        return rows;
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
