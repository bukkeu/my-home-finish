package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 stock
*  */
public interface StockRepository extends JpaRepository<Stock, Long> {

    /** 통합 검색(#15) : 사용 중인 종목 중 이름에 키워드가 들어간 것 */
    List<Stock> findByNameContainingAndIsActiveTrueOrderByStockIdAsc(String keyword);
}
