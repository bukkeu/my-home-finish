package com.wanted.moneybeetle.global.common;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/* comment.
 *   담당 : SCM (회원·공통) : 강의 내용 x
 *   목록 응답 형식 { items, page, size, totalElements, totalPages } (API 명세서 1-3)
 *   사용 : ApiResponse.success(PageResponse.from(page))
 *  */

@Getter
public class PageResponse<T> {

    private final List<T> items;        // 이번 쪽 데이터
    private final int page;             // 현재 쪽 번호 (0부터)
    private final int size;             // 한 쪽 크기
    private final long totalElements;   // 전체 개수
    private final int totalPages;       // 전체 쪽 수

    private PageResponse(List<T> items, int page, int size, long totalElements, int totalPages) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    // 스프링 Data의 Page 상자를 우리 팀 목록 형식으로 옮겨 담는다
    public static <T> PageResponse<T> from(Page<T> pageData) {
        return new PageResponse<>(
                pageData.getContent(),
                pageData.getNumber(),
                pageData.getSize(),
                pageData.getTotalElements(),
                pageData.getTotalPages()
        );
    }
}