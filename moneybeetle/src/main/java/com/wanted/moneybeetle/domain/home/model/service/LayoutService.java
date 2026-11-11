package com.wanted.moneybeetle.domain.home.model.service;

import com.wanted.moneybeetle.domain.growth.model.dto.AttendanceStatusDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.LevelDTO;
import com.wanted.moneybeetle.domain.growth.model.service.AttendanceService;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.home.model.dto.LayoutDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberProfileDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import com.wanted.moneybeetle.global.security.UnlockFeature;
import com.wanted.moneybeetle.global.security.UnlockLevelProvider;
import org.springframework.stereotype.Service;

/* comment.
*   담당 : DBA ② (홈, 이슈 #248)
*   공통 레이아웃(헤더·햄버거 메뉴)이 쓰는 LayoutDTO 를 만든다.
*   - 회원 프로필(닉네임·레벨·경험치·캐시)은 member, 칭호·레벨별 필요 경험치·레벨업 캐시는 growth 의 레벨표(API 24 와 같은 값),
*     연속 출석과 보상까지 남은 일수는 growth 의 출석 현황(API 21)에서 가져온다. 다른 도메인은 Service 메서드만 부른다.
*   - 경험치 막대는 "이번 레벨에서 얼마나 모았나"(내 경험치 - 이번 레벨 시작 경험치) / (다음 레벨 시작 경험치 - 이번 레벨 시작 경험치) 이다.
*     최대 레벨이면 막대 계산에 쓸 다음 레벨이 없어 0 / 0 으로 두고, 다음 레벨업 캐시도 0 이다.
*   - 해금 레벨은 운영 설정(system_setting)에서 UnlockLevelProvider 로 읽는다 (모의투자 기본 8, 모의 청약 기본 12, #266).
*   - 이 클래스는 트랜잭션을 열지 않는다. 각 서비스 호출이 자기 읽기 전용 트랜잭션을 연다.
*  */
@Service
@RequiredArgsConstructor
public class LayoutService {

    private final MemberService memberService;
    private final GrowthService growthService;
    private final AttendanceService attendanceService;
    private final UnlockLevelProvider unlockLevelProvider;

    public LayoutDTO getLayout(Long memberId) {
        MemberProfileDTO profile = memberService.getActiveProfile(memberId);
        List<LevelDTO> levels = growthService.findLevels();
        AttendanceStatusDTO attendance = attendanceService.getStatus(memberId);

        // 레벨표 범위 안으로 맞춘다 (표보다 높은 값이 들어 있어도 화면이 깨지지 않게)
        int level = Math.max(1, Math.min(profile.level(), levels.size()));
        LevelDTO current = levels.get(level - 1);
        boolean maxLevel = level >= levels.size();

        int expInLevel = 0;
        int expPerLevel = 0;
        long nextLevelCash = 0;
        if (!maxLevel) {
            LevelDTO next = levels.get(level);
            expInLevel = Math.max(0, profile.exp() - current.getRequiredExp());
            expPerLevel = next.getRequiredExp() - current.getRequiredExp();
            nextLevelCash = next.getLevelupCash();
        }

        return new LayoutDTO(profile.nickname(), level, current.getTitle(), profile.cash(), expInLevel, expPerLevel,
                nextLevelCash, attendance.getStreak(), attendanceHint(attendance),
                unlockLevelProvider.getLevel(UnlockFeature.TRADING), unlockLevelProvider.getLevel(UnlockFeature.HOUSING));
    }

    /** 사이드 메뉴의 연속 출석 안내 문구. 보상 규칙이 꺼져 있으면(보상 캐시 0) 캐시 이야기를 하지 않는다 */
    static String attendanceHint(AttendanceStatusDTO attendance) {
        long cash = attendance.getRewardCash();
        if (cash <= 0) {
            return "";
        }
        String amount = NumberFormat.getIntegerInstance(Locale.KOREA).format(cash);
        if (attendance.getDaysToReward() > 0) {
            return attendance.getDaysToReward() + "일 더 출석하면 매일 캐시 " + amount + "을 받아요";
        }
        return "연속 출석 중 · 매일 캐시 " + amount + " 지급";
    }
}
