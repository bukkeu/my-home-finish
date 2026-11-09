package com.wanted.moneybeetle.domain.growth.model.dao;

import com.wanted.moneybeetle.domain.growth.model.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   테이블 attendance
*  */
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

}
