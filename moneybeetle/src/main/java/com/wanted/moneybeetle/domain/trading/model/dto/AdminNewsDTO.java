package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   관리자 뉴스 목록 한 줄 (API 131) : 관리자 메모와 관련 분야·종목(번호·이름) 포함
*  */

import com.wanted.moneybeetle.domain.trading.model.entity.News;
import java.util.List;
import lombok.Getter;

@Getter
public class AdminNewsDTO {

    private final Long newsId;
    private final int turnNo;
    private final String title;
    private final String content;
    private final String adminMemo;
    private final List<Tag> sectors;
    private final List<Tag> stocks;

    private AdminNewsDTO(Long newsId, int turnNo, String title, String content, String adminMemo,
            List<Tag> sectors, List<Tag> stocks) {
        this.newsId = newsId;
        this.turnNo = turnNo;
        this.title = title;
        this.content = content;
        this.adminMemo = adminMemo;
        this.sectors = sectors;
        this.stocks = stocks;
    }

    public static AdminNewsDTO of(News news, List<Tag> sectors, List<Tag> stocks) {
        return new AdminNewsDTO(news.getNewsId(), news.getTurnNo(), news.getTitle(), news.getContent(),
                news.getAdminMemo(), sectors, stocks);
    }

    /** 관련 분야 또는 종목 (번호와 이름) */
    @Getter
    public static class Tag {

        private final Long id;
        private final String name;

        private Tag(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public static Tag of(Long id, String name) {
            return new Tag(id, name);
        }
    }
}
