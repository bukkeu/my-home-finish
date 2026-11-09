package com.wanted.moneybeetle.domain.growth.model.service;

import com.wanted.moneybeetle.domain.growth.model.entity.LevelPolicy;

import java.util.ArrayList;
import java.util.List;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   테스트용 레벨 정책표. 이슈 #11 확정값을 그대로 만든다 (sample-data.sql 시드와 같아야 한다: LevelSeedTest 가 확인한다)
*   - 레벨이 한 칸 오르는 데 필요한 경험치: 고시원(Lv.1~3) 150 · 원룸(4~7) 200 · 투룸(8~11) 300 · 아파트(12~16) 400 · 내 집(17~19) 600
*   - 레벨업 캐시 = 새 레벨 × 10,000 (Lv.1 은 0), 최대 Lv.20
*  */
public final class LevelTableFixture {

    private LevelTableFixture() {
    }

    public static List<LevelPolicy> policies() {
        List<LevelPolicy> policies = new ArrayList<>();
        int exp = 0;
        for (int level = 1; level <= 20; level++) {
            policies.add(LevelPolicy.create(level, exp, titleOf(level), level == 1 ? 0 : level * 10_000));
            exp += costToNext(level);
        }
        return policies;
    }

    public static LevelTable table() {
        return new LevelTable(policies());
    }

    private static int costToNext(int level) {
        if (level <= 3) {
            return 150;
        }
        if (level <= 7) {
            return 200;
        }
        if (level <= 11) {
            return 300;
        }
        if (level <= 16) {
            return 400;
        }
        return 600;
    }

    private static String titleOf(int level) {
        if (level <= 3) {
            return "고시원";
        }
        if (level <= 7) {
            return "원룸";
        }
        if (level <= 11) {
            return "투룸";
        }
        if (level <= 16) {
            return "아파트";
        }
        return "내 집";
    }
}
