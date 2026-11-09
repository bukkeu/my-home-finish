package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.Sector;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 sector
*  */
public interface SectorRepository extends JpaRepository<Sector, Long> {

    /** 사용 중인 분야를 정렬 순서대로 (#24) */
    List<Sector> findByIsActiveTrueOrderBySortOrderAscSectorIdAsc();
}
