package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.News;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 news
*  */
public interface NewsRepository extends JpaRepository<News, Long> {

    /** 현재 턴까지 공개된 뉴스, 최근 턴부터 (#24) */
    List<News> findByTradingSeasonIdAndTurnNoLessThanEqualOrderByTurnNoDescNewsIdDesc(Long tradingSeasonId, int turnNo);

    /** 그 턴에 입력된 뉴스 수 (다음 턴 점검, #44) */
    long countByTradingSeasonIdAndTurnNo(Long tradingSeasonId, int turnNo);
}
