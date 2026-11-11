package com.wanted.moneybeetle.domain.growth.model.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** API 22 response: current cash balance and a page of cash history. */
public record CashHistoryPageDTO(
        long cash,
        List<CashHistoryDTO> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static CashHistoryPageDTO from(long cash, Page<CashHistoryDTO> historyPage) {
        return new CashHistoryPageDTO(
                cash,
                historyPage.getContent(),
                historyPage.getNumber(),
                historyPage.getSize(),
                historyPage.getTotalElements(),
                historyPage.getTotalPages());
    }
}
