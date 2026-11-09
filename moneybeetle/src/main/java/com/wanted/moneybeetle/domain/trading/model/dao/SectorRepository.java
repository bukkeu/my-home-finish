package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.Sector;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 sector
*  */
public interface SectorRepository extends JpaRepository<Sector, Long> {

    /** 정렬 순서대로 (같으면 번호 순) */
    List<Sector> findAllByOrderBySortOrderAscSectorIdAsc();

    boolean existsByName(String name);

    /** 수정할 때 자기 자신을 뺀 이름 중복 확인 */
    boolean existsByNameAndSectorIdNot(String name, Long sectorId);
}
