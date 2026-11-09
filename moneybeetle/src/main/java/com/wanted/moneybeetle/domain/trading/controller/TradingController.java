package com.wanted.moneybeetle.domain.trading.controller;

/* comment.
*   담당 : PM (모의투자)
*   GET  /api/v1/trading/home, /sectors, /stocks, /stocks/{stockId}, /news, /trade-reasons
*   POST /api/v1/trading/seasons/{seasonId}/participations
*   화면 : /user/demo-trading
*   - #24 : /sectors, /stocks, /stocks/{stockId}, /news (모두 Lv.8 해금, LevelCheckInterceptor 가 확인)
*  */

import com.wanted.moneybeetle.domain.trading.model.dto.NewsDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SectorStocksDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.StockDetailDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.StockListDTO;
import com.wanted.moneybeetle.domain.trading.model.service.TradingService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.security.RequireLevel;
import com.wanted.moneybeetle.global.security.UnlockFeature;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trading")
@RequireLevel(UnlockFeature.TRADING)
@RequiredArgsConstructor
public class TradingController {

    private final TradingService tradingService;

    // API 55 분야 목록
    @GetMapping("/sectors")
    public ApiResponse<Map<String, List<SectorStocksDTO>>> findSectors() {
        return ApiResponse.success(Map.of("sectors", tradingService.findSectors()));
    }

    // API 56 오늘의 가상 시세 : GET /api/v1/trading/stocks?sort=popular&sectorId=&keyword=
    @GetMapping("/stocks")
    public ApiResponse<StockListDTO> findStocks(@RequestParam(required = false) String sort,
            @RequestParam(required = false) Long sectorId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(tradingService.findStocks(sort, sectorId, keyword));
    }

    // API 57 종목 상세
    @GetMapping("/stocks/{stockId}")
    public ApiResponse<StockDetailDTO> getStock(@PathVariable Long stockId) {
        return ApiResponse.success(tradingService.getStock(stockId, currentMemberId()));
    }

    // API 58 가상 뉴스 : GET /api/v1/trading/news?turnNo=5&stockId=1
    @GetMapping("/news")
    public ApiResponse<Map<String, List<NewsDTO>>> findNews(@RequestParam(required = false) Integer turnNo,
            @RequestParam(required = false) Long stockId) {
        return ApiResponse.success(Map.of("items", tradingService.findNews(turnNo, stockId)));
    }

    // TODO(김예린 #6) 로그인 회원 번호 : AuthDetails.getMemberId() 가 생기면 바꾼다
    //  지금은 null → 종목 상세의 myHolding 은 항상 null (HousingController 와 같은 방식)
    private Long currentMemberId() {
        return null;
    }
}
