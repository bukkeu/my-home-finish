package com.wanted.moneybeetle.domain.growth.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   테이블 level_policy - 레벨별 필요 누적 경험치, 칭호, 레벨업 캐시 (SP-14, SP-20, SP-21)
*   - 읽기 전용 정책표다. 값은 시드(sample-data.sql)·관리자 설정으로 정하고 코드에서 바꾸지 않는다
*   - required_exp 는 "그 레벨이 되기 위한 누적 경험치"다 (Lv.1 = 0)
*  */
@Entity
@Table(name = "level_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LevelPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long levelPolicyId;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private int requiredExp;

    @Column(nullable = false, length = 20)
    private String title;

    /** 이 레벨에 올랐을 때 받는 캐시 (새 레벨 × 10,000, SP-14). Lv.1 은 0 */
    @Column(nullable = false)
    private int levelupCash;

    /** 테스트용 정적 팩토리 */
    public static LevelPolicy create(int level, int requiredExp, String title, int levelupCash) {
        LevelPolicy policy = new LevelPolicy();
        policy.level = level;
        policy.requiredExp = requiredExp;
        policy.title = title;
        policy.levelupCash = levelupCash;
        return policy;
    }
}
