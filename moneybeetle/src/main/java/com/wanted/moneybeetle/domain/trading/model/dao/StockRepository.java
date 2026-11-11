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

    List<Stock> findAllByOrderByStockIdAsc();

    boolean existsByName(String name);

    /** 수정할 때 자기 자신을 뺀 이름 중복 확인 */
    boolean existsByNameAndStockIdNot(String name, Long stockId);

    /** 분야의 사용 중 종목 수 (분야당 2개 규칙, ADM-17) */
    long countBySectorIdAndIsActiveTrue(Long sectorId);

    /** 사용 중인 종목 (#24) */
    List<Stock> findByIsActiveTrueOrderByStockIdAsc();
}
