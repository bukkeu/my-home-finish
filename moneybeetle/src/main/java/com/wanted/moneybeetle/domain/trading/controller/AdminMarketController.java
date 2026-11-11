package com.wanted.moneybeetle.domain.trading.controller;

/* comment.
*   담당 : PM (모의투자)
*   분야·종목 (#41) : GET/POST /api/v1/admin/trading/sectors, /stocks, PUT /sectors/{id}, /stocks/{id}
*   가격표(#42) : GET/PUT /seasons/{id}/prices, GET /prices/export, POST /prices/import (CSV)
*   뉴스(#43) : GET/POST /seasons/{id}/news, PUT/DELETE /news/{newsId}
*   권한 : /api/v1/admin/** 는 SecurityConfig 에서 ADMIN 만
*  */

import com.wanted.moneybeetle.domain.trading.model.dto.AdminStockDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.PriceSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.PriceSaveResultDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.PriceTableDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SectorDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SectorSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.StockSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.service.MarketAdminService;
import com.wanted.moneybeetle.domain.trading.model.service.PriceAdminService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/trading")
@RequiredArgsConstructor
public class AdminMarketController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final MarketAdminService marketAdminService;
    private final PriceAdminService priceAdminService;

    // API 127 턴별 가격표
    @GetMapping("/seasons/{seasonId}/prices")
    public ApiResponse<PriceTableDTO> findPriceTable(@PathVariable Long seasonId) {
        return ApiResponse.success(priceAdminService.findPriceTable(seasonId));
    }

    // API 128 턴별 가격 저장 (경고가 있고 confirm=false 면 saved=0 + warnings)
    @PutMapping("/seasons/{seasonId}/prices")
    public ApiResponse<PriceSaveResultDTO> savePrices(@PathVariable Long seasonId,
            @Valid @RequestBody PriceSaveDTO request) {
        return ApiResponse.success(priceAdminService.savePrices(seasonId, request));
    }

    // API 129 가격표 CSV 내려받기
    @GetMapping("/seasons/{seasonId}/prices/export")
    public ResponseEntity<byte[]> exportPrices(@PathVariable Long seasonId) {
        byte[] csv = priceAdminService.exportCsv(seasonId).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("season-" + seasonId + "-prices.csv").build().toString())
                .body(csv);
    }

    // API 130 가격표 CSV 올리기 (오류 행이 있으면 400 CSV_INVALID + data.errors)
    @PostMapping(value = "/seasons/{seasonId}/prices/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PriceSaveResultDTO> importPrices(@PathVariable Long seasonId,
            @RequestParam("file") MultipartFile file) throws IOException {
        try (InputStream csv = file.getInputStream()) {   // 성공·실패 모두 스트림을 닫는다
            return ApiResponse.success(priceAdminService.importCsv(seasonId, csv));
        }
    }

    // API 121 분야 목록
    @GetMapping("/sectors")
    public ApiResponse<Map<String, List<SectorDTO>>> findSectors() {
        return ApiResponse.success(Map.of("items", marketAdminService.findSectors()));
    }

    // API 122 분야 등록 (201)
    @PostMapping("/sectors")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Long>> createSector(@Valid @RequestBody SectorSaveDTO request) {
        return ApiResponse.success(Map.of("sectorId", marketAdminService.createSector(request)));
    }

    // API 123 분야 수정 (204)
    @PutMapping("/sectors/{sectorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeSector(@PathVariable Long sectorId, @Valid @RequestBody SectorSaveDTO request) {
        marketAdminService.changeSector(sectorId, request);
    }

    // API 124 종목 목록
    @GetMapping("/stocks")
    public ApiResponse<Map<String, List<AdminStockDTO>>> findStocks() {
        return ApiResponse.success(Map.of("items", marketAdminService.findStocks()));
    }

    // API 125 종목 등록 (201)
    @PostMapping("/stocks")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Long>> createStock(@Valid @RequestBody StockSaveDTO request) {
        return ApiResponse.success(Map.of("stockId", marketAdminService.createStock(request)));
    }

    // API 126 종목·기업정보 수정 (204)
    @PutMapping("/stocks/{stockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeStock(@PathVariable Long stockId, @Valid @RequestBody StockSaveDTO request) {
        marketAdminService.changeStock(stockId, request);
    }
}
