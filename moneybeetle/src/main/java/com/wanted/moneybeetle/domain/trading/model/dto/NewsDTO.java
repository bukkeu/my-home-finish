package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   뉴스 (관련 분야·종목) - API 58
*   - 관리자 메모(admin_memo)는 사용자에게 보이지 않으므로 담지 않는다
*  */

import com.wanted.moneybeetle.domain.trading.model.entity.News;
import java.util.List;
import lombok.Getter;

@Getter
public class NewsDTO {

    private final Long newsId;
    private final int turnNo;
    private final String title;
    private final String content;
    private final List<String> sectors;
    private final List<String> stocks;

    private NewsDTO(Long newsId, int turnNo, String title, String content, List<String> sectors,
            List<String> stocks) {
        this.newsId = newsId;
        this.turnNo = turnNo;
        this.title = title;
        this.content = content;
        this.sectors = sectors;
        this.stocks = stocks;
    }

    public static NewsDTO of(News news, List<String> sectors, List<String> stocks) {
        return new NewsDTO(news.getNewsId(), news.getTurnNo(), news.getTitle(), news.getContent(), sectors, stocks);
    }
}
