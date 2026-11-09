package com.wanted.moneybeetle.domain.operation.model.dao;

import com.wanted.moneybeetle.domain.operation.model.entity.AdminLog;
import java.time.LocalDateTime;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   테이블 admin_log
*   - 기록은 남기기만 하고(INSERT 만, DP-43) 고치거나 지우지 않는다. 조회는 관리자 작업 기록 화면용(API 113)이다.
*  */
public interface AdminLogRepository extends JpaRepository<AdminLog, Long> {

    /**
     * 대상·관리자·기간으로 최신순 조회한다. 응답에 전체 건수가 없어서 건수를 세는 쿼리를 따로 날리지 않도록 Slice 로 돌려준다. 값이 null 인 조건은 쓰지 않는다. 기간은 from 이상, to 미만이다.
     */
    @Query("select l from AdminLog l "
            + "where (:targetType is null or l.targetType = :targetType) "
            + "and (:targetId is null or l.targetId = :targetId) "
            + "and (:adminId is null or l.adminId = :adminId) "
            + "and (:from is null or l.createdAt >= :from) "
            + "and (:to is null or l.createdAt < :to) "
            + "order by l.createdAt desc, l.adminLogId desc")
    Slice<AdminLog> search(@Param("targetType") String targetType, @Param("targetId") Long targetId,
                          @Param("adminId") Long adminId, @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to, Pageable pageable);
}
