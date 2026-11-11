package com.wanted.moneybeetle.domain.operation.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   테이블 system_setting (해금 레벨, 퀴즈 통과 점수 등 코드 수정 없이 바꾸는 값, NFR-13)
*   - 키(setting_key)는 시드로 정해져 있고 관리자는 값만 바꾼다. 키를 새로 만들거나 지우지 않는다.
*   - 다른 도메인(member)은 Entity 가 아니라 ID(updatedBy)로 참조한다.
*  */
@Entity
@Table(name = "system_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemSetting {

    public static final int VALUE_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long systemSettingId;

    @Column(nullable = false, length = 50, unique = true)
    private String settingKey;

    @Column(nullable = false, length = VALUE_MAX_LENGTH)
    private String settingValue;

    @Column(length = 200)
    private String description;

    /** 마지막으로 바꾼 관리자 */
    private Long updatedBy;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** 테스트와 시드 용도의 정적 팩토리 */
    public static SystemSetting create(String settingKey, String settingValue, String description) {
        SystemSetting setting = new SystemSetting();
        setting.settingKey = settingKey;
        setting.settingValue = settingValue;
        setting.description = description;
        return setting;
    }

    /** 값을 바꾼다. 비어 있거나 200자를 넘으면 IllegalArgumentException (형식 검사는 DTO·Service 가 먼저 한다) */
    public void changeValue(String value, Long adminId) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("설정 값이 필요합니다.");
        }
        if (value.length() > VALUE_MAX_LENGTH) {
            throw new IllegalArgumentException("설정 값은 " + VALUE_MAX_LENGTH + "자까지 쓸 수 있습니다.");
        }
        this.settingValue = value;
        this.updatedBy = adminId;
    }
}
