package com.wanted.moneybeetle.domain.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.growth.model.dto.AttendanceStatusDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.LevelPolicy;
import com.wanted.moneybeetle.domain.growth.model.service.AttendanceService;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.growth.model.service.LevelTable;
import com.wanted.moneybeetle.domain.member.model.dto.MemberProfileDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MyInfoDTO;
import com.wanted.moneybeetle.domain.member.model.dto.ProfileImageDTO;
import com.wanted.moneybeetle.domain.member.model.dto.PublicProfileDTO;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import com.wanted.moneybeetle.global.file.FileStorageService;
import com.wanted.moneybeetle.domain.operation.model.service.SystemSettingService;
import com.wanted.moneybeetle.global.security.UnlockLevelProvider;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

/* comment.
*   담당 : DBA ② (회원·인증)
*   MemberProfileService (#7) - 다른 Service 를 Mock 으로 두고 서버·DB 없이 돈다
*  */
class MemberProfileServiceTest {

    private MemberService memberService;
    private GrowthService growthService;
    private CourseService courseService;
    private FileStorageService fileStorageService;
    private AttendanceService attendanceService;
    private SystemSettingService systemSettingService;
    private MemberProfileService service;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        growthService = mock(GrowthService.class);
        courseService = mock(CourseService.class);
        fileStorageService = mock(FileStorageService.class);
        attendanceService = mock(AttendanceService.class);
        systemSettingService = mock(SystemSettingService.class);   // 설정이 없으면 기본 해금 레벨 8·12
        service = new MemberProfileService(memberService, growthService, courseService, attendanceService, fileStorageService,
                new UnlockLevelProvider(systemSettingService));

        LevelTable table = levelTable();   // when() 안에서 다른 Mock 을 만들지 않도록 먼저 만든다
        when(growthService.loadLevelTable()).thenReturn(table);
        when(courseService.findWatchHistories(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(1), 14));
        when(courseService.findCompletedCourses(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(1), 5));
    }

    /** 레벨 1~20, 필요 경험치 = (레벨-1)*150, 칭호는 레벨 번호 */
    private static LevelTable levelTable() {
        List<LevelPolicy> policies = new ArrayList<>();
        for (int level = 1; level <= 20; level++) {
            LevelPolicy policy = mock(LevelPolicy.class);
            when(policy.getLevel()).thenReturn(level);
            when(policy.getRequiredExp()).thenReturn((level - 1) * 150);
            when(policy.getTitle()).thenReturn("칭호" + level);
            when(policy.getLevelupCash()).thenReturn(0);
            policies.add(policy);
        }
        return new LevelTable(policies);
    }

    private static MemberProfileDTO profile(int level, int exp, int streak) {
        return new MemberProfileDTO(1L, "minji@gmail.com", "민지", null, LocalDate.of(1999, 4, 12), "FEMALE",
                "MEMBER", level, exp, 12400L, streak);
    }

    @Test
    @DisplayName("내 정보에 칭호·다음 레벨까지 경험치·출석·학습·해금이 함께 나온다")
    void myInfo() {
        when(memberService.getProfile(1L)).thenReturn(profile(8, 1120, 3));
        when(attendanceService.getStatus(1L)).thenReturn(new AttendanceStatusDTO(3, 2, 10_000, true));

        MyInfoDTO info = service.getMyInfo(1L);

        assertThat(info.email()).isEqualTo("minji@gmail.com");
        assertThat(info.title()).isEqualTo("칭호8");
        assertThat(info.expToNextLevel()).isEqualTo(8 * 150 - 1120);
        assertThat(info.attendance().streak()).isEqualTo(3);
        assertThat(info.attendance().daysToReward()).isEqualTo(2);
        assertThat(info.learning().watchedLessons()).isEqualTo(14);
        assertThat(info.learning().completedCourses()).isEqualTo(5);
        assertThat(info.unlocks().trading()).isTrue();
        assertThat(info.unlocks().housing()).isFalse();
        assertThat(info.unlocks().housingLevel()).isEqualTo(12);
    }

    @Test
    @DisplayName("최대 레벨이면 다음 레벨까지 경험치는 0, 연속 5일 이상이면 보상까지 0일")
    void maxLevel() {
        when(memberService.getProfile(1L)).thenReturn(profile(20, 99999, 7));
        when(attendanceService.getStatus(1L)).thenReturn(new AttendanceStatusDTO(7, 0, 10_000, true));

        MyInfoDTO info = service.getMyInfo(1L);

        assertThat(info.expToNextLevel()).isZero();
        assertThat(info.attendance().daysToReward()).isZero();
        assertThat(info.unlocks().housing()).isTrue();
    }

    @Test
    @DisplayName("#238 관리자가 해금 레벨을 바꾸면 내 정보도 그 값으로 해금 여부·필요 레벨을 안내한다 (청약 8·15, 모의투자 6·10)")
    void unlocksFollowOperationSettings() {
        when(attendanceService.getStatus(1L)).thenReturn(new AttendanceStatusDTO(3, 2, 10_000, true));

        // 청약 8, 모의투자 6 : Lv.8 회원은 둘 다 열림, 청약 필요 레벨 8
        when(systemSettingService.findIntValue("UNLOCK_LEVEL_HOUSING")).thenReturn(OptionalInt.of(8));
        when(systemSettingService.findIntValue("UNLOCK_LEVEL_TRADING")).thenReturn(OptionalInt.of(6));
        when(memberService.getProfile(1L)).thenReturn(profile(8, 1120, 3));
        MyInfoDTO lowered = service.getMyInfo(1L);
        assertThat(lowered.unlocks().housing()).isTrue();
        assertThat(lowered.unlocks().housingLevel()).isEqualTo(8);
        assertThat(lowered.unlocks().trading()).isTrue();

        // 청약 15, 모의투자 10 : Lv.12 회원은 청약 잠김(필요 15), Lv.9 회원은 모의투자도 잠김
        when(systemSettingService.findIntValue("UNLOCK_LEVEL_HOUSING")).thenReturn(OptionalInt.of(15));
        when(systemSettingService.findIntValue("UNLOCK_LEVEL_TRADING")).thenReturn(OptionalInt.of(10));
        when(memberService.getProfile(1L)).thenReturn(profile(12, 1800, 3));
        MyInfoDTO lv12 = service.getMyInfo(1L);
        assertThat(lv12.unlocks().housing()).isFalse();
        assertThat(lv12.unlocks().housingLevel()).isEqualTo(15);
        assertThat(lv12.unlocks().trading()).isTrue();

        when(memberService.getProfile(1L)).thenReturn(profile(9, 1300, 3));
        assertThat(service.getMyInfo(1L).unlocks().trading()).isFalse();
    }

    @Test
    @DisplayName("공개 프로필에는 닉네임·칭호·레벨만 있고 이메일·생년월일·성별·캐시 필드가 없다")
    void publicProfileHasNoPersonalInfo() {
        when(memberService.getActiveProfile(2L)).thenReturn(profile(15, 2100, 0));

        PublicProfileDTO result = service.getPublicProfile(2L);

        assertThat(result.nickname()).isEqualTo("민지");
        assertThat(result.title()).isEqualTo("칭호15");
        assertThat(result.awards()).isEmpty();
        assertThat(PublicProfileDTO.class.getRecordComponents())
                .extracting(c -> c.getName())
                .doesNotContain("email", "birthDate", "gender", "cash", "exp");
    }

    @Test
    @DisplayName("프로필 이미지는 파일을 저장한 뒤 주소를 회원에게 기록한다")
    void changesProfileImage() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});
        when(fileStorageService.saveImage(file)).thenReturn("/uploads/abc.png");
        when(memberService.changeProfileImageUrl(1L, "/uploads/abc.png")).thenReturn(new ProfileImageDTO("/uploads/abc.png"));

        assertThat(service.changeProfileImage(1L, file).profileImageUrl()).isEqualTo("/uploads/abc.png");
    }

    @Test
    @DisplayName("빈 파일은 저장하지 않고 FILE_TYPE_NOT_ALLOWED")
    void emptyFileRejected() throws IOException {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.changeProfileImage(1L, empty))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FILE_TYPE_NOT_ALLOWED));
        verify(fileStorageService, never()).saveImage(any());
    }

    @Test
    @DisplayName("파일 저장 중 입출력 오류가 나면 INTERNAL_ERROR 이고 주소는 기록하지 않는다")
    void ioErrorMapped() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});
        when(fileStorageService.saveImage(file)).thenThrow(new IOException("disk"));

        assertThatThrownBy(() -> service.changeProfileImage(1L, file))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_ERROR));
        verify(memberService, never()).changeProfileImageUrl(any(), any());
    }

    @Test
    @DisplayName("연속 출석은 회원 행에 저장된 값이 아니라 출석 현황(오늘 기준)을 쓴다 - 어제 빠졌으면 저장값이 3이어도 0일이다")
    void attendanceUsesStatusNotStoredStreak() {
        when(memberService.getProfile(1L)).thenReturn(profile(8, 1120, 3));   // 저장된 연속 일수는 3 이지만 하루 빠져서 끊겼다
        when(attendanceService.getStatus(1L)).thenReturn(new AttendanceStatusDTO(0, 5, 10_000, false));

        MyInfoDTO info = service.getMyInfo(1L);

        assertThat(info.attendance().streak()).isZero();
        assertThat(info.attendance().daysToReward()).isEqualTo(AttendanceService.REWARD_START_STREAK);
    }
}
