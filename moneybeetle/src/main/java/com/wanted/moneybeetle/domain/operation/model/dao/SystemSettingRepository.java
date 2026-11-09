package com.wanted.moneybeetle.domain.operation.model.dao;

import com.wanted.moneybeetle.domain.operation.model.entity.SystemSetting;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   테이블 system_setting
*  */
public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {

    List<SystemSetting> findAllByOrderBySystemSettingIdAsc();

    Optional<SystemSetting> findBySettingKey(String settingKey);
}
