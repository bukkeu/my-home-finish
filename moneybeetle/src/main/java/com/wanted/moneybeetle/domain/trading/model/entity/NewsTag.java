package com.wanted.moneybeetle.domain.trading.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
*   담당 : DBA ② (모의투자)
*   테이블 news_tag (뉴스 <-> 분야·종목 N:M)
*  */
@Entity
@Table(name = "news_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long newsTagId;

    private Long newsId;

    /** 관련 분야 (분야 태그일 때만) */
    private Long sectorId;

    /** 관련 종목 (종목 태그일 때만) */
    private Long stockId;

    private NewsTag(Long newsId, Long sectorId, Long stockId) {
        this.newsId = newsId;
        this.sectorId = sectorId;
        this.stockId = stockId;
    }

    public static NewsTag createForSector(Long newsId, Long sectorId) {
        return new NewsTag(newsId, sectorId, null);
    }

    public static NewsTag createForStock(Long newsId, Long stockId) {
        return new NewsTag(newsId, null, stockId);
    }

    public boolean isSectorTag() {
        return this.sectorId != null;
    }

    public boolean isStockTag() {
        return this.stockId != null;
    }
}
