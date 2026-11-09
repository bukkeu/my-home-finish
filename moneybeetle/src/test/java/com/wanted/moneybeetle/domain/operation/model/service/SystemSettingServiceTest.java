package com.wanted.moneybeetle.domain.operation.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.operation.exception.OperationErrorCode;
import com.wanted.moneybeetle.domain.operation.model.dao.SystemSettingRepository;
import com.wanted.moneybeetle.domain.operation.model.entity.SystemSetting;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class SystemSettingServiceTest {

    private SystemSettingRepository repository;
    private AdminLogService adminLogService;
    private SystemSettingService service;
    private SystemSetting trading;
    private SystemSetting text;

    @BeforeEach
    void setUp() {
        repository = mock(SystemSettingRepository.class);
        adminLogService = mock(AdminLogService.class);
        service = new SystemSettingService(repository, adminLogService);
        trading = SystemSetting.create("UNLOCK_LEVEL_TRADING", "8", "모의투자 해금 레벨");
        ReflectionTestUtils.setField(trading, "systemSettingId", 2L);
        text = SystemSetting.create("SERVICE_NOTICE", "점검 예정", null);
        ReflectionTestUtils.setField(text, "systemSettingId", 9L);
        when(repository.findBySettingKey("UNLOCK_LEVEL_TRADING")).thenReturn(Optional.of(trading));
        when(repository.findBySettingKey("SERVICE_NOTICE")).thenReturn(Optional.of(text));
    }

    @Test
    @DisplayName("모든 설정을 키·값으로 돌려준다")
    void findsSettings() {
        when(repository.findAllByOrderBySystemSettingIdAsc()).thenReturn(List.of(trading, text));

        var result = service.findSettings();

        assertThat(result.settings()).extracting("key").containsExactly("UNLOCK_LEVEL_TRADING", "SERVICE_NOTICE");
        assertThat(result.settings().get(0).value()).isEqualTo("8");
    }

    @Test
    @DisplayName("값을 바꾸면 마지막 수정 관리자를 남기고 변경 전·후를 admin_log 에 기록한다")
    void updatesAndLogs() {
        service.updateSetting(1L, "UNLOCK_LEVEL_TRADING", " 12 ");

        assertThat(trading.getSettingValue()).isEqualTo("12");
        assertThat(trading.getUpdatedBy()).isEqualTo(1L);
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(adminLogService).record(eq(1L), eq("SETTING_UPDATE"), eq("SYSTEM_SETTING"), eq(2L), detail.capture());
        assertThat(detail.getValue()).contains("UNLOCK_LEVEL_TRADING").contains("\"before\":\"8\"").contains("\"after\":\"12\"");
    }

    @Test
    @DisplayName("값이 그대로면 기록하지 않는다")
    void unchangedIsNotLogged() {
        service.updateSetting(1L, "UNLOCK_LEVEL_TRADING", "8");

        verify(adminLogService, never()).record(any(), any(), any(), any(), any());
        assertThat(trading.getUpdatedBy()).isNull();
    }

    @Test
    @DisplayName("숫자 설정에 숫자가 아닌 값을 넣으면 SETTING_VALUE_INVALID 이고 값은 그대로다")
    void rejectsNonNumberForNumericSetting() {
        assertThatThrownBy(() -> service.updateSetting(1L, "UNLOCK_LEVEL_TRADING", "열두"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(OperationErrorCode.SETTING_VALUE_INVALID));

        assertThat(trading.getSettingValue()).isEqualTo("8");
        verify(adminLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("문자 설정에는 어떤 값이든 넣을 수 있다")
    void textSettingAcceptsAnyValue() {
        service.updateSetting(1L, "SERVICE_NOTICE", "내일 점검");

        assertThat(text.getSettingValue()).isEqualTo("내일 점검");
    }

    @Test
    @DisplayName("없는 키이면 NOT_FOUND")
    void unknownKey() {
        when(repository.findBySettingKey("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSetting(1L, "NOPE", "1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("숫자 모양이어도 int 범위를 넘는 값은 숫자 설정에 넣을 수 없다 (SETTING_VALUE_INVALID)")
    void rejectsNumberOutOfIntRange() {
        assertThatThrownBy(() -> service.updateSetting(1L, "UNLOCK_LEVEL_TRADING", "99999999999"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(OperationErrorCode.SETTING_VALUE_INVALID));

        assertThat(trading.getSettingValue()).isEqualTo("8");
        verify(adminLogService, never()).record(any(), any(), any(), any(), any());
    }
}
