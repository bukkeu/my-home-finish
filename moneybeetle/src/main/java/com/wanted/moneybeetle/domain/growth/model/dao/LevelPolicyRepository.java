package com.wanted.moneybeetle.domain.growth.model.dao;

import com.wanted.moneybeetle.domain.growth.model.entity.LevelPolicy;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   테이블 level_policy
*  */
public interface LevelPolicyRepository extends JpaRepository<LevelPolicy, Long> {

    /** 레벨 낮은 순 전체 (20줄 안팎의 작은 표라서 한 번에 읽는다) */
    List<LevelPolicy> findAllByOrderByLevelAsc();
}
