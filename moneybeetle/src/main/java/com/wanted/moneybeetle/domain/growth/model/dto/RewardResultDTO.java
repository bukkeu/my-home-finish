package com.wanted.moneybeetle.domain.growth.model.dto;

import java.util.List;
import lombok.Getter;

/* comment.
*   담당 : DBA ② (성장)
*   RewardService.grant 의 결과 (다른 도메인이 응답에 그대로 싣는다)
*   - 이미 받은 활동이면 alreadyGranted = true, 지급량은 0 (예외를 던지지 않는다)
*   - 경험치로 레벨이 올랐으면 levelFrom -> levelTo 가 달라지고, 레벨업 캐시(levelUpCash)와
*     새로 열린 기능(unlocks: TRADING·HOUSING)이 채워진다 (#11). grantedCash 는 규칙이 준 캐시이고 레벨업 캐시는 따로다.
*  */
@Getter
public class RewardResultDTO {

    private final boolean alreadyGranted;
    private final int grantedExp;
    private final long grantedCash;
    /** 레벨이 오르며 받은 레벨업 캐시 합계 (오르지 않았으면 0) */
    private final long levelUpCash;
    /** 지급을 모두 마친 뒤의 캐시 잔액 (레벨업 캐시 포함) */
    private final long cashBalance;
    private final int expTotal;
    private final int levelFrom;
    private final int levelTo;
    /** 이번 레벨업으로 새로 열린 기능 (없으면 빈 목록) */
    private final List<String> unlocks;

    private RewardResultDTO(boolean alreadyGranted, int grantedExp, long grantedCash, long levelUpCash,
                            long cashBalance, int expTotal, int levelFrom, int levelTo, List<String> unlocks) {
        this.alreadyGranted = alreadyGranted;
        this.grantedExp = grantedExp;
        this.grantedCash = grantedCash;
        this.levelUpCash = levelUpCash;
        this.cashBalance = cashBalance;
        this.expTotal = expTotal;
        this.levelFrom = levelFrom;
        this.levelTo = levelTo;
        this.unlocks = unlocks;
    }

    /** 새로 지급했다 */
    public static RewardResultDTO granted(int grantedExp, long grantedCash, long levelUpCash, long cashBalance,
                                          int expTotal, int levelFrom, int levelTo, List<String> unlocks) {
        return new RewardResultDTO(false, grantedExp, grantedCash, levelUpCash, cashBalance, expTotal,
                levelFrom, levelTo, unlocks);
    }

    /** 이미 받은 활동이다. 현재 값만 알려 주고 지급은 하지 않았다 */
    public static RewardResultDTO alreadyGranted(long cashBalance, int expTotal, int level) {
        return new RewardResultDTO(true, 0, 0, 0, cashBalance, expTotal, level, level, List.of());
    }

    /** 이번 지급으로 레벨이 올랐는가 */
    public boolean isLevelUp() {
        return levelTo > levelFrom;
    }
}
