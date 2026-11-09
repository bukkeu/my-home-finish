package com.wanted.moneybeetle.domain.operation.model.dto;

import com.wanted.moneybeetle.domain.operation.model.entity.SystemSetting;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   서비스 설정 변경 요청 (API 112) { value }
*   - 입력값만으로 판단되는 규칙(비어 있지 않음, 200자 이하)은 여기서 확인한다.
*  */
public record SettingUpdateDTO(@NotBlank @Size(max = SystemSetting.VALUE_MAX_LENGTH) String value) {
}
