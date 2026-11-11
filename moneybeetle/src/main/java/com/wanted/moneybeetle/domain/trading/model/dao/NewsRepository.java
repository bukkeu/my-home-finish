package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.News;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* comment.
*   담당 : PM (모의투자)
*   테이블 news
*  */
public interface NewsRepository extends JpaRepository<News, Long> {

    /** 관리자 뉴스 목록 : 시즌 전체, 턴 순서 (#43) */
    List<News> findByTradingSeasonIdOrderByTurnNoAscNewsIdAsc(Long tradingSeasonId);

    /** 관리자 뉴스 목록 : 한 턴만 (#43) */
    List<News> findByTradingSeasonIdAndTurnNoOrderByNewsIdAsc(Long tradingSeasonId, int turnNo);

    /** 현재 턴까지 공개된 뉴스, 최근 턴부터 (#24) */
    List<News> findByTradingSeasonIdAndTurnNoLessThanEqualOrderByTurnNoDescNewsIdDesc(Long tradingSeasonId, int turnNo);

    /** 뉴스가 속한 시즌 번호만 읽는다. News 객체를 영속성 컨텍스트에 올리지 않아 시즌 잠금 뒤 최신 뉴스를 다시 읽을 수 있다 (#44) */
    @Query("select n.tradingSeasonId from News n where n.newsId = :newsId")
    Optional<Long> findSeasonIdByNewsId(@Param("newsId") Long newsId);

    /** 뉴스를 잠금 읽기로 읽는다(최신 값). 시즌 행 잠금을 잡은 뒤에 부른다 (#44) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from News n where n.newsId = :newsId")
    Optional<News> findByIdForUpdate(@Param("newsId") Long newsId);

    /** 그 턴에 입력된 뉴스 수 (다음 턴 점검, #44) */
    long countByTradingSeasonIdAndTurnNo(Long tradingSeasonId, int turnNo);
}
