package com.wanted.moneybeetle.domain.operation.model.dao;

import com.wanted.moneybeetle.domain.operation.model.entity.SystemSetting;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   테이블 system_setting
*  */
public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {

    List<SystemSetting> findAllByOrderBySystemSettingIdAsc();

    Optional<SystemSetting> findBySettingKey(String settingKey);

    /**
     * 관리자가 설정을 바꾸려고 행을 잠그고 읽는다 (비관적 락, 트랜잭션이 끝날 때까지 유지).
     * 두 관리자가 같은 설정을 동시에 바꿔도 차례로 처리되어, 작업 기록의 변경 전·후 값이 실제 변경 순서와 이어진다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SystemSetting s where s.settingKey = :settingKey")
    Optional<SystemSetting> findBySettingKeyForUpdate(@Param("settingKey") String settingKey);
}
