package com.wanted.moneybeetle.domain.growth.model.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** API 23 response: current level and experience with a page of experience history. */
public record ExpHistoryPageDTO(
        int level,
        int exp,
        List<ExpHistoryDTO> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static ExpHistoryPageDTO from(int level, int exp, Page<ExpHistoryDTO> historyPage) {
        return new ExpHistoryPageDTO(
                level,
                exp,
                historyPage.getContent(),
                historyPage.getNumber(),
                historyPage.getSize(),
                historyPage.getTotalElements(),
                historyPage.getTotalPages());
    }
}
