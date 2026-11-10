package com.wanted.moneybeetle.domain.operation.model.dto;

import com.wanted.moneybeetle.domain.operation.model.entity.SystemSetting;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   서비스 설정 한 건 (API 111 응답) { key, value }
*  */
public record SettingDTO(String key, String value) {

    public static SettingDTO from(SystemSetting setting) {
        return new SettingDTO(setting.getSettingKey(), setting.getSettingValue());
    }
}
