package com.wanted.moneybeetle.domain.home.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.growth.model.dto.AttendanceStatusDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.LevelDTO;
import com.wanted.moneybeetle.domain.growth.model.service.AttendanceService;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.home.model.dto.LayoutDTO;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dto.MemberProfileDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : DBA ② (홈, 이슈 #248)
*   LayoutService - 헤더·햄버거 메뉴가 쓰는 값(닉네임·레벨·칭호·캐시·경험치 막대·연속 출석)이 맞게 만들어지는지 서버·DB 없이 확인한다.
*  */
class LayoutServiceTest {

    private final MemberService memberService = mock(MemberService.class);
    private final GrowthService growthService = mock(GrowthService.class);
    private final AttendanceService attendanceService = mock(AttendanceService.class);
    private LayoutService service;

    @BeforeEach
    void setUp() {
        service = new LayoutService(memberService, growthService, attendanceService);
        // 레벨표: Lv.1 0xp 고시원, Lv.2 100xp (캐시 1,000), Lv.3 250xp (캐시 2,000)
        List<LevelDTO> levels = List.of(level(1, 0, "고시원", 0), level(2, 100, "고시원", 1000), level(3, 250, "고시원", 2000));
        when(growthService.findLevels()).thenReturn(levels);
        when(attendanceService.getStatus(7L)).thenReturn(new AttendanceStatusDTO(4, 1, 1000, false));
    }

    private static LevelDTO level(int level, int requiredExp, String title, int levelupCash) {
        LevelDTO dto = mock(LevelDTO.class);
        when(dto.getLevel()).thenReturn(level);
        when(dto.getRequiredExp()).thenReturn(requiredExp);
        when(dto.getTitle()).thenReturn(title);
        when(dto.getLevelupCash()).thenReturn(levelupCash);
        return dto;
    }

    private void givenProfile(int level, int exp, long cash) {
        when(memberService.getActiveProfile(7L)).thenReturn(new MemberProfileDTO(7L, "minji@gmail.com", "민지", null,
                LocalDate.of(1999, 4, 12), "FEMALE", "MEMBER", level, exp, cash, 4));
    }

    @Test
    @DisplayName("닉네임·레벨·칭호·캐시와, 이번 레벨 안에서 모은 경험치 / 다음 레벨까지 필요한 경험치, 다음 레벨업 캐시가 채워진다")
    void fillsValues() {
        givenProfile(2, 160, 12400);

        LayoutDTO layout = service.getLayout(7L);

        assertThat(layout.getNickname()).isEqualTo("민지");
        assertThat(layout.getLevel()).isEqualTo(2);
        assertThat(layout.getTierName()).isEqualTo("고시원");
        assertThat(layout.getCash()).isEqualTo(12400);
        assertThat(layout.getExpInLevel()).isEqualTo(60);       // 160 - 100
        assertThat(layout.getExpPerLevel()).isEqualTo(150);     // 250 - 100
        assertThat(layout.getExpPercent()).isEqualTo(40);
        assertThat(layout.getNextLevelCash()).isEqualTo(2000);  // Lv.3 에 오를 때 받는 캐시
        assertThat(layout.getAttendanceStreak()).isEqualTo(4);
        assertThat(layout.getUnlockTradingLevel()).isEqualTo(8);
        assertThat(layout.getUnlockHousingLevel()).isEqualTo(12);
    }

    @Test
    @DisplayName("최대 레벨이면 막대 계산에 쓸 다음 레벨이 없어 0 / 0 이고 다음 레벨업 캐시도 0 이다")
    void maxLevel() {
        givenProfile(3, 999, 100);

        LayoutDTO layout = service.getLayout(7L);

        assertThat(layout.getLevel()).isEqualTo(3);
        assertThat(layout.getExpInLevel()).isZero();
        assertThat(layout.getExpPerLevel()).isZero();
        assertThat(layout.getExpPercent()).isZero();
        assertThat(layout.getNextLevelCash()).isZero();
    }

    @Test
    @DisplayName("레벨표보다 높거나 1보다 낮은 레벨 값이 있어도 표 범위 안으로 맞춘다")
    void clampsLevel() {
        givenProfile(99, 0, 0);
        assertThat(service.getLayout(7L).getLevel()).isEqualTo(3);

        givenProfile(0, 0, 0);
        assertThat(service.getLayout(7L).getLevel()).isEqualTo(1);
    }

    @Test
    @DisplayName("경험치가 이번 레벨 시작 경험치보다 작게 들어 있어도 막대는 음수가 되지 않는다")
    void expNeverNegative() {
        givenProfile(2, 50, 0);

        assertThat(service.getLayout(7L).getExpInLevel()).isZero();
    }

    @Test
    @DisplayName("연속 출석 안내: 보상까지 남은 날이 있으면 남은 일수, 보상 구간이면 지급 중, 보상 규칙이 꺼져 있으면 빈 문구")
    void attendanceHints() {
        assertThat(LayoutService.attendanceHint(new AttendanceStatusDTO(4, 1, 1000, false))).isEqualTo("1일 더 출석하면 매일 캐시 1,000을 받아요");
        assertThat(LayoutService.attendanceHint(new AttendanceStatusDTO(6, 0, 1000, true))).isEqualTo("연속 출석 중 · 매일 캐시 1,000 지급");
        assertThat(LayoutService.attendanceHint(new AttendanceStatusDTO(2, 3, 0, false))).isEmpty();
    }

    @Test
    @DisplayName("탈퇴·정지 회원이면 만들지 못하고 MEMBER_NOT_FOUND 를 그대로 던진다 (호출하는 인터셉터가 기본값으로 넘어간다)")
    void inactiveMember() {
        when(memberService.getActiveProfile(7L)).thenThrow(new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        assertThatThrownBy(() -> service.getLayout(7L)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
