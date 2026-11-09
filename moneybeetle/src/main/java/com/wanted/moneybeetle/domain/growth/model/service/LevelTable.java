package com.wanted.moneybeetle.domain.growth.model.service;

import com.wanted.moneybeetle.domain.growth.model.entity.LevelPolicy;
import java.util.List;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   레벨 정책표(level_policy)로 계산하는 규칙 (DB 없이 값만으로 판단한다)
*   - 누적 경험치 -> 레벨 (levelFor), 레벨 -> 칭호·레벨업 캐시, 여러 레벨이 한 번에 오를 때의 레벨업 캐시 합 (SP-23)
*   - 표가 올바른지(1레벨부터 빠짐없이, 필요 경험치가 계속 늘어남)를 만들 때 한 번 확인한다
*  */
public class LevelTable {

    private final List<LevelPolicy> policies;

    public LevelTable(List<LevelPolicy> sortedPolicies) {
        if (sortedPolicies == null || sortedPolicies.isEmpty()) {
            throw new IllegalArgumentException("레벨 정책이 비어 있습니다.");
        }
        for (int i = 0; i < sortedPolicies.size(); i++) {
            LevelPolicy policy = sortedPolicies.get(i);
            if (policy.getLevel() != i + 1) {
                throw new IllegalArgumentException("레벨은 1부터 빠짐없이 이어져야 합니다: " + (i + 1) + " 자리에 " + policy.getLevel());
            }
            if (i == 0 && policy.getRequiredExp() != 0) {
                throw new IllegalArgumentException("Lv.1 의 필요 경험치는 0 이어야 합니다.");
            }
            if (i > 0 && policy.getRequiredExp() <= sortedPolicies.get(i - 1).getRequiredExp()) {
                throw new IllegalArgumentException("필요 경험치는 레벨이 오를수록 커져야 합니다: Lv." + policy.getLevel());
            }
            if (policy.getLevelupCash() < 0) {
                throw new IllegalArgumentException("레벨업 캐시는 0 이상이어야 합니다: Lv." + policy.getLevel());
            }
        }
        this.policies = List.copyOf(sortedPolicies);
    }

    public int maxLevel() {
        return policies.size();
    }

    /** 누적 경험치로 도달한 레벨. 최대 레벨을 넘는 경험치는 최대 레벨로 본다 */
    public int levelFor(int exp) {
        int level = 1;
        for (LevelPolicy policy : policies) {
            if (exp >= policy.getRequiredExp()) {
                level = policy.getLevel();
            }
        }
        return level;
    }

    /** 해당 레벨의 정책. 범위 밖이면 IllegalArgumentException */
    public LevelPolicy policyOf(int level) {
        if (level < 1 || level > maxLevel()) {
            throw new IllegalArgumentException("레벨은 1~" + maxLevel() + " 사이여야 합니다: " + level);
        }
        return policies.get(level - 1);
    }

    public String titleOf(int level) {
        return policyOf(level).getTitle();
    }

    /** from 레벨 다음부터 to 레벨까지 오르며 받는 레벨업 캐시의 합 (한 번에 여러 레벨이 오르면 레벨마다 모두 준다, SP-23) */
    public long levelUpCashBetween(int fromLevel, int toLevel) {
        long sum = 0;
        for (int level = fromLevel + 1; level <= toLevel; level++) {
            sum += policyOf(level).getLevelupCash();
        }
        return sum;
    }

    /** 전체 정책 (레벨 낮은 순) */
    public List<LevelPolicy> all() {
        return policies;
    }
}
