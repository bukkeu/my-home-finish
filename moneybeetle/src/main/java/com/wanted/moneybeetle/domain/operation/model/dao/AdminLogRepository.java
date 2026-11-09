package com.wanted.moneybeetle.domain.operation.model.dao;

import com.wanted.moneybeetle.domain.operation.model.entity.AdminLog;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   테이블 admin_log
*   - 기록은 남기기만 한다 (INSERT 만, DP-43). 조회 화면(관리자 작업 기록 API, #39)을 만들 때 조회 메서드를 더한다.
*  */
public interface AdminLogRepository extends JpaRepository<AdminLog, Long> {

}
