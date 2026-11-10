package com.wanted.moneybeetle.domain.growth.model.entity;

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
import org.hibernate.annotations.UpdateTimestamp;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   테이블 reward_rule (활동별 캐시·경험치 보상 규칙)
*   값(캐시·경험치 양)은 이슈 #68·#76 에서 확정하며, 시드(sample-data.sql)와 관리자 화면(#39)에서 바뀐다.
*  */
@Entity
@Table(name = "reward_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RewardRule {

    /** 강의 학습 완료 (SP-22) */
    public static final String COURSE_COMPLETE = "COURSE_COMPLETE";
    /** 퀴즈 만점 보너스 (SP-22) */
    public static final String QUIZ_PERFECT = "QUIZ_PERFECT";
    /** 연속 출석 보상 (SP-10~12) */
    public static final String ATTENDANCE_STREAK = "ATTENDANCE_STREAK";
    /** 출석 경험치 기본값 (SP-22, #205). 연속 일수 구간에 따라 AttendanceService 가 배수(1~5)를 곱해 10~50 XP 를 준다 */
    public static final String ATTENDANCE_DAILY = "ATTENDANCE_DAILY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long rewardRuleId;

    /** 규칙 코드 (위 상수). 테이블에서 UNIQUE */
    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    /** 지급 캐시 */
    private int cashAmount;

    /** 지급 경험치 */
    private int expAmount;

    @Column(length = 200)
    private String description;

    /** 사용 여부. 꺼진 규칙은 지급하지 않는다 */
    @Column(name = "is_active")
    private boolean active;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private RewardRule(String code, String name, int cashAmount, int expAmount, String description) {
        validateAmount(cashAmount, expAmount);
        this.code = code;
        this.name = name;
        this.cashAmount = cashAmount;
        this.expAmount = expAmount;
        this.description = description;
        this.active = true;
    }

    public static RewardRule create(String code, String name, int cashAmount, int expAmount, String description) {
        return new RewardRule(code, name, cashAmount, expAmount, description);
    }

    /** 관리자 화면(#39)에서 지급 양을 바꾼다. 음수는 불가 */
    public void changeAmounts(int cashAmount, int expAmount) {
        validateAmount(cashAmount, expAmount);
        this.cashAmount = cashAmount;
        this.expAmount = expAmount;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    /** 이 규칙으로 줄 것이 있는가 (캐시·경험치가 둘 다 0이면 지급할 게 없다) */
    public boolean hasReward() {
        return cashAmount > 0 || expAmount > 0;
    }

    private static void validateAmount(int cashAmount, int expAmount) {
        if (cashAmount < 0 || expAmount < 0) {
            throw new IllegalArgumentException("보상 값은 0 이상이어야 합니다.");
        }
    }
}
