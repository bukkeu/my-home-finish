package com.wanted.moneybeetle.domain.growth.model.dto;

import lombok.Getter;

/* comment.
*   담당 : DBA ② (성장)
*   RewardService.grant 의 결과 (다른 도메인이 응답에 그대로 싣는다)
*   - 이미 받은 활동이면 alreadyGranted = true, 지급량은 0 (예외를 던지지 않는다)
*   - 레벨 변화(levelFrom -> levelTo)와 해금 기능은 #11(레벨업·칭호)에서 채운다
*  */
@Getter
public class RewardResultDTO {

    private final boolean alreadyGranted;
    private final int grantedExp;
    private final long grantedCash;
    private final long cashBalance;
    private final int expTotal;
    private final int levelFrom;
    private final int levelTo;

    private RewardResultDTO(boolean alreadyGranted, int grantedExp, long grantedCash,
                            long cashBalance, int expTotal, int levelFrom, int levelTo) {
        this.alreadyGranted = alreadyGranted;
        this.grantedExp = grantedExp;
        this.grantedCash = grantedCash;
        this.cashBalance = cashBalance;
        this.expTotal = expTotal;
        this.levelFrom = levelFrom;
        this.levelTo = levelTo;
    }

    /** 새로 지급했다 */
    public static RewardResultDTO granted(int grantedExp, long grantedCash, long cashBalance,
                                          int expTotal, int levelFrom, int levelTo) {
        return new RewardResultDTO(false, grantedExp, grantedCash, cashBalance, expTotal, levelFrom, levelTo);
    }

    /** 이미 받은 활동이다. 현재 값만 알려 주고 지급은 하지 않았다 */
    public static RewardResultDTO alreadyGranted(long cashBalance, int expTotal, int level) {
        return new RewardResultDTO(true, 0, 0, cashBalance, expTotal, level, level);
    }

    /** 이번 지급으로 레벨이 올랐는가 */
    public boolean isLevelUp() {
        return levelTo > levelFrom;
    }
}
