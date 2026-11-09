package com.wanted.moneybeetle.domain.operation.model.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.operation.exception.OperationErrorCode;
import com.wanted.moneybeetle.domain.operation.model.dao.SystemSettingRepository;
import com.wanted.moneybeetle.domain.operation.model.dto.SettingDTO;
import com.wanted.moneybeetle.domain.operation.model.dto.SettingListDTO;
import com.wanted.moneybeetle.domain.operation.model.entity.SystemSetting;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   서비스 설정 조회·변경 (이슈 #39, API 111·112, NFR-13). 다른 도메인이 값을 읽는 메서드는 읽는 쪽 이슈에서 더한다.
*   - findSettings : 모든 설정(키·값)을 돌려준다
*   - updateSetting : 값을 바꾸고, 실제로 바뀌었을 때만 admin_log(SETTING_UPDATE)에 변경 전·후를 남긴다 (같은 트랜잭션).
*       키는 시드로 정해져 있어 없는 키는 NOT_FOUND(404). 지금 값이 숫자인 설정에 숫자가 아닌 값을 넣으면
*       SETTING_VALUE_INVALID(400) 로 막는다 (해금 레벨·통과 점수를 읽는 쪽이 깨지지 않게).
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemSettingService {

    static final String ACTION_UPDATE = "SETTING_UPDATE";
    static final String TARGET_TYPE = "SYSTEM_SETTING";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SystemSettingRepository systemSettingRepository;
    private final AdminLogService adminLogService;

    public SettingListDTO findSettings() {
        return new SettingListDTO(systemSettingRepository.findAllByOrderBySystemSettingIdAsc().stream()
                .map(SettingDTO::from)
                .toList());
    }

    @Transactional
    public void updateSetting(Long adminId, String key, String value) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        String newValue = value == null ? "" : value.strip();
        if (isInteger(setting.getSettingValue()) && !isInteger(newValue)) {
            throw new BusinessException(OperationErrorCode.SETTING_VALUE_INVALID);
        }

        String before = setting.getSettingValue();
        if (before.equals(newValue)) {
            return;
        }
        setting.changeValue(newValue, adminId);
        adminLogService.record(adminId, ACTION_UPDATE, TARGET_TYPE, setting.getSystemSettingId(),
                toJson(key, before, newValue));
    }

    private static boolean isInteger(String text) {
        return text != null && text.matches("-?\\d+");
    }

    private static String toJson(String key, String before, String after) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("key", key);
        detail.put("before", before);
        detail.put("after", after);
        try {
            return JSON.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("작업 기록을 만들지 못했습니다.", e);
        }
    }
}
