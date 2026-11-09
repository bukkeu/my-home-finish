package com.wanted.moneybeetle.domain.growth.model.dao;

import com.wanted.moneybeetle.domain.growth.model.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   테이블 attendance
*  */
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

}
