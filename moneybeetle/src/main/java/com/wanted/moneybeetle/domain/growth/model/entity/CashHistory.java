package com.wanted.moneybeetle.domain.growth.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   테이블 cash_history (원장, INSERT 만 한다. 수정·삭제 메서드는 두지 않는다)
*   - 잔액은 0 미만이 될 수 없다 (SP-15)
*   - 같은 (회원, 원인 종류, 원인 ID)는 한 번만 기록된다 (DB UNIQUE uk_cash_history_1) -> 이중 지급 방지
*  */
@Entity
@Table(name = "cash_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashHistory {

    /** 내역 종류 (history_type) */
    public static final String EARN = "EARN";
    public static final String USE = "USE";
    public static final String ADMIN_GIVE = "ADMIN_GIVE";
    public static final String ADMIN_TAKE = "ADMIN_TAKE";

    /**
     * 원인 종류 (source_type)의 옛 문자열 상수. 원인 종류는 {@link CashSource} enum 으로 옮겼다 (이슈 #287).
     * 다른 도메인(housing 등)이 아직 이 상수를 쓰고 있어서 값(enum 이름과 같은 문자열)만 남겨 둔다.
     * 모두 옮기면 마지막 정리 이슈(#301)에서 지운다.
     */
    @Deprecated
    public static final String SOURCE_ATTENDANCE = "ATTENDANCE";
    @Deprecated
    public static final String SOURCE_COURSE_COMPLETE = "COURSE_COMPLETE";
    @Deprecated
    public static final String SOURCE_LEVEL_UP = "LEVEL_UP";
    @Deprecated
    public static final String SOURCE_SEASON_JOIN = "SEASON_JOIN";
    @Deprecated
    public static final String SOURCE_SUBSCRIPTION_DEPOSIT = "SUBSCRIPTION_DEPOSIT";
    @Deprecated
    public static final String SOURCE_ADMIN = "ADMIN";
    @Deprecated
    public static final String SOURCE_RANKING = "RANKING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cashHistoryId;

    /** 다른 도메인(member)은 Entity가 아니라 ID로 참조한다 */
    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 20)
    private String historyType;

    /** 원인 종류. DB 에는 enum 이름이 문자열로 저장된다 (컬럼은 그대로 VARCHAR) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CashSource sourceType;

    /** 원인 데이터 ID (출석 ID, 강의 ID, 레벨 등). 관리자 조정은 NULL 가능 */
    private Long sourceId;

    private Long rewardRuleId;

    /** 변동 금액. 사용·회수는 음수 */
    private long amount;

    /** 변동 후 잔액 */
    private long balanceAfter;

    /** 관리자 지급·회수 사유 (필수) */
    @Column(length = 200)
    private String reason;

    private Long adminId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private CashHistory(Long memberId, String historyType, CashSource sourceType, Long sourceId, Long rewardRuleId,
                        long amount, long balanceAfter, String reason, Long adminId) {
        this.memberId = memberId;
        this.historyType = historyType;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.rewardRuleId = rewardRuleId;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.reason = reason;
        this.adminId = adminId;
    }

    /** 활동 보상으로 캐시를 받았다 (amount > 0) */
    public static CashHistory earn(Long memberId, CashSource sourceType, Long sourceId, Long rewardRuleId,
                                   long amount, long balanceAfter) {
        validate(amount > 0, "적립 금액은 0보다 커야 합니다.");
        validate(balanceAfter >= 0, "잔액은 0 미만이 될 수 없습니다.");
        return new CashHistory(memberId, EARN, sourceType, sourceId, rewardRuleId, amount, balanceAfter, null, null);
    }

    /** 시즌 참가·청약통장 납입 등으로 캐시를 썼다. 금액은 양수로 받아 음수로 기록한다 */
    public static CashHistory use(Long memberId, CashSource sourceType, Long sourceId, long amount, long balanceAfter) {
        validate(amount > 0, "사용 금액은 0보다 커야 합니다.");
        validate(balanceAfter >= 0, "잔액은 0 미만이 될 수 없습니다.");
        return new CashHistory(memberId, USE, sourceType, sourceId, null, -amount, balanceAfter, null, null);
    }

    /** 관리자가 캐시를 수동 지급했다. 사유 필수 */
    public static CashHistory adminGive(Long memberId, Long adminId, long amount, long balanceAfter, String reason) {
        validate(amount > 0, "지급 금액은 0보다 커야 합니다.");
        validate(reason != null && !reason.isBlank(), "관리자 지급 사유를 입력해야 합니다.");
        validate(balanceAfter >= 0, "잔액은 0 미만이 될 수 없습니다.");
        return new CashHistory(memberId, ADMIN_GIVE, CashSource.ADMIN, null, null, amount, balanceAfter, reason, adminId);
    }

    /** 관리자가 캐시를 수동 회수했다. 금액은 양수로 받아 음수로 기록한다. 사유 필수 */
    public static CashHistory adminTake(Long memberId, Long adminId, long amount, long balanceAfter, String reason) {
        validate(amount > 0, "회수 금액은 0보다 커야 합니다.");
        validate(reason != null && !reason.isBlank(), "관리자 회수 사유를 입력해야 합니다.");
        validate(balanceAfter >= 0, "잔액은 0 미만이 될 수 없습니다.");
        return new CashHistory(memberId, ADMIN_TAKE, CashSource.ADMIN, null, null, -amount, balanceAfter, reason, adminId);
    }

    /** 잔액이 늘어난 내역인가 (적립·관리자 지급) */
    public boolean isIncome() {
        return amount > 0;
    }

    private static void validate(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }
}
