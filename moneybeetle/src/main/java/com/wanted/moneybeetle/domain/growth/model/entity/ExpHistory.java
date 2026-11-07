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

/* comment.
*   담당 : DBA ② (성장)
*   테이블 exp_history (원장, INSERT 만 한다. 수정·삭제 메서드는 두지 않는다)
*   - 같은 (회원, 원인 종류, 원인 강의 ID)는 한 번만 기록된다 (DB UNIQUE uk_exp_history_1) -> 같은 강의 최초 1회만 (SP-22)
*  */
@Entity
@Table(name = "exp_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpHistory {

    /** 원인 종류 (source_type) */
    public static final String SOURCE_COURSE_COMPLETE = "COURSE_COMPLETE";
    public static final String SOURCE_QUIZ_PERFECT = "QUIZ_PERFECT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long expHistoryId;

    /** 다른 도메인(member)은 Entity가 아니라 ID로 참조한다 */
    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 30)
    private String sourceType;

    /** 원인 강의 ID */
    @Column(nullable = false)
    private Long sourceId;

    private Long rewardRuleId;

    /** 지급 경험치 */
    private int amount;

    /** 지급 후 누적 경험치 */
    private int expAfter;

    /** 지급 후 레벨 */
    private int levelAfter;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private ExpHistory(Long memberId, String sourceType, Long sourceId, Long rewardRuleId,
                       int amount, int expAfter, int levelAfter) {
        this.memberId = memberId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.rewardRuleId = rewardRuleId;
        this.amount = amount;
        this.expAfter = expAfter;
        this.levelAfter = levelAfter;
    }

    public static ExpHistory create(Long memberId, String sourceType, Long sourceId, Long rewardRuleId,
                                    int amount, int expAfter, int levelAfter) {
        if (amount <= 0) {
            throw new IllegalArgumentException("지급 경험치는 0보다 커야 합니다.");
        }
        if (expAfter < amount) {
            throw new IllegalArgumentException("누적 경험치는 지급 경험치보다 작을 수 없습니다.");
        }
        if (levelAfter < 1 || levelAfter > 20) {
            throw new IllegalArgumentException("레벨은 1~20 사이여야 합니다.");
        }
        return new ExpHistory(memberId, sourceType, sourceId, rewardRuleId, amount, expAfter, levelAfter);
    }
}
