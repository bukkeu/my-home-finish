package com.wanted.moneybeetle.domain.home.model.dto;

import lombok.Getter;

/* comment.
*   담당 : DBA ② (홈)
*   공통 레이아웃(헤더·햄버거 메뉴)이 쓰는 값. Controller 가 Model 에 "layout" 이름으로 넘긴다.
*   - 닉네임·레벨·캐시·경험치·연속 출석은 회원 정보에서, 해금 레벨은 system_setting(UNLOCK_LEVEL_TRADING·HOUSING)에서 채운다.
*   - 채우는 쪽(모든 화면 공통 ControllerAdvice)은 회원·성장 서비스가 올라온 뒤(#4, #10, #11) 만든다.
*   - 템플릿은 layout 이 null 이어도 기본값으로 그려지므로, 이 값이 없어도 화면은 깨지지 않는다.
*  */
@Getter
public class LayoutDTO {

    private final String nickname;
    private final int level;
    private final String tierName;
    private final long cash;
    private final int expInLevel;
    private final int expPerLevel;
    private final long nextLevelCash;
    private final int attendanceStreak;
    private final String attendanceHint;
    private final int unlockTradingLevel;
    private final int unlockHousingLevel;

    public LayoutDTO(String nickname, int level, String tierName, long cash, int expInLevel, int expPerLevel,
                     long nextLevelCash, int attendanceStreak, String attendanceHint,
                     int unlockTradingLevel, int unlockHousingLevel) {
        this.nickname = nickname;
        this.level = level;
        this.tierName = tierName;
        this.cash = cash;
        this.expInLevel = expInLevel;
        this.expPerLevel = expPerLevel;
        this.nextLevelCash = nextLevelCash;
        this.attendanceStreak = attendanceStreak;
        this.attendanceHint = attendanceHint;
        this.unlockTradingLevel = unlockTradingLevel;
        this.unlockHousingLevel = unlockHousingLevel;
    }

    /** 닉네임 첫 글자 (프로필 동그라미에 보이는 글자). 닉네임이 없으면 ? */
    public String getInitial() {
        if (nickname == null || nickname.isBlank()) {
            return "?";
        }
        return nickname.substring(0, nickname.offsetByCodePoints(0, 1));
    }

    /** 이번 레벨 경험치 막대의 채움 비율 (0~100) */
    public int getExpPercent() {
        if (expPerLevel <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, expInLevel * 100 / expPerLevel));
    }
}
