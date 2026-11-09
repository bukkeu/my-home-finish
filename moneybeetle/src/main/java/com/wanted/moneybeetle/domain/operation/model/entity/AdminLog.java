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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   테이블 admin_log - 관리자 작업 기록 (INSERT 만, DP-43)
*   - 한 번 남기면 고치거나 지우지 않는다. 그래서 값을 바꾸는 메서드와 Setter 를 두지 않는다.
*   - 다른 도메인(member·course·season)은 Entity 가 아니라 ID(adminId, targetId)로 참조한다.
*   - detail 은 변경 전·후 값을 담은 JSON 문자열이다 (비워도 된다).
*  */
@Entity
@Table(name = "admin_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminLog {

    public static final int ACTION_MAX_LENGTH = 50;
    public static final int TARGET_TYPE_MAX_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long adminLogId;

    /** 작업한 관리자 */
    @Column(nullable = false)
    private Long adminId;

    /** 무엇을 했나. 예: COURSE_APPROVE, COURSE_REJECT, TURN_ADVANCE, CASH_GIVE */
    @Column(nullable = false, length = ACTION_MAX_LENGTH)
    private String action;

    /** 대상 종류. 예: COURSE, SEASON, MEMBER */
    @Column(nullable = false, length = TARGET_TYPE_MAX_LENGTH)
    private String targetType;

    /** 대상 ID. 특정 대상이 없는 작업이면 비어 있다 */
    private Long targetId;

    /** 변경 전·후 값 (JSON 문자열) */
    @JdbcTypeCode(SqlTypes.JSON)
    private String detail;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private AdminLog(Long adminId, String action, String targetType, Long targetId, String detail) {
        this.adminId = adminId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
    }

    /**
     * 새 기록. 값이 규칙에 맞지 않으면 IllegalArgumentException (호출하는 코드의 실수이지 사용자 입력 오류가 아니다).
     * detail 이 JSON 형식인지는 AdminLogService 가 확인한다.
     */
    public static AdminLog create(Long adminId, String action, String targetType, Long targetId, String detail) {
        if (adminId == null) {
            throw new IllegalArgumentException("작업한 관리자가 필요합니다.");
        }
        requireText(action, ACTION_MAX_LENGTH, "action");
        requireText(targetType, TARGET_TYPE_MAX_LENGTH, "targetType");
        return new AdminLog(adminId, action, targetType, targetId, detail);
    }

    private static void requireText(String value, int maxLength, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 은 비어 있을 수 없습니다.");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " 은 " + maxLength + "자까지 쓸 수 있습니다.");
        }
    }
}
