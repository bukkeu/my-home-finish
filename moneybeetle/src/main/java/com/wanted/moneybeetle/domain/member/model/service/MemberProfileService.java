package com.wanted.moneybeetle.domain.member.model.service;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.growth.model.dto.AttendanceStatusDTO;
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
import com.wanted.moneybeetle.global.security.UnlockFeature;
import com.wanted.moneybeetle.global.security.UnlockLevelProvider;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/* comment.
*   담당 : DBA ② (회원·인증)
*   내 정보·공개 프로필·프로필 이미지 (이슈 #7, API 13·15·18)
*   - 레벨 칭호·다음 레벨까지 경험치는 growth.GrowthService, 연속 출석·보상까지 남은 일수는 growth.AttendanceService(출석 현황, API 21과 같은 값),
*     학습 수는 course.CourseService 를 거쳐 받는다 (다른 도메인 Repository 는 쓰지 않는다).
*   - growth.GrowthService 가 MemberService 를 쓰므로, MemberService 가 growth 를 부르면 서로 물려 버린다.
*     그래서 여러 도메인 값을 모으는 일은 MemberService 가 아니라 이 클래스가 맡는다.
*   - 트랜잭션은 부르는 Service 메서드가 각각 연다. 이 클래스는 읽기와 파일 저장만 한다.
*  */
@Service
@RequiredArgsConstructor
public class MemberProfileService {

    private final MemberService memberService;
    private final GrowthService growthService;
    private final CourseService courseService;
    private final AttendanceService attendanceService;
    private final FileStorageService fileStorageService;
    /** 해금 레벨은 운영 설정(관리자 API 112)에서 읽는다 → 모의투자·모의 청약 API·화면과 같은 기준 (#238) */
    private final UnlockLevelProvider unlockLevelProvider;

    /** 내 정보 (API 13) */
    public MyInfoDTO getMyInfo(Long memberId) {
        MemberProfileDTO profile = memberService.getProfile(memberId);
        LevelTable levelTable = growthService.loadLevelTable();
        int level = profile.level();

        // 최대 레벨이면 남은 경험치가 없다
        int expToNextLevel = level >= levelTable.maxLevel()
                ? 0
                : Math.max(0, levelTable.policyOf(level + 1).getRequiredExp() - profile.exp());

        // 연속 출석은 회원 행에 저장된 값이 아니라 오늘(서울) 기준으로 이어지고 있는 일수다. 어제 빠졌으면 0이고, 남은 일수도 같은 기준으로 계산된다
        AttendanceStatusDTO attendance = attendanceService.getStatus(memberId);
        long watchedLessons = courseService.findWatchHistories(memberId, PageRequest.of(0, 1)).getTotalElements();
        long completedCourses = courseService.findCompletedCourses(memberId, PageRequest.of(0, 1)).getTotalElements();
        int tradingLevel = unlockLevelProvider.getLevel(UnlockFeature.TRADING);
        int housingLevel = unlockLevelProvider.getLevel(UnlockFeature.HOUSING);

        return new MyInfoDTO(
                profile.memberId(), profile.email(), profile.nickname(), profile.profileImageUrl(),
                profile.birthDate(), profile.gender(), profile.role(),
                level, levelTable.titleOf(level), profile.exp(), expToNextLevel, profile.cash(),
                new MyInfoDTO.Attendance(attendance.getStreak(), attendance.getDaysToReward()),
                new MyInfoDTO.Learning(watchedLessons, completedCourses),
                new MyInfoDTO.Unlocks(level >= tradingLevel, level >= housingLevel, housingLevel));
    }

    /** 다른 회원 공개 프로필 (API 18). 개인정보는 담지 않는다 */
    public PublicProfileDTO getPublicProfile(Long memberId) {
        MemberProfileDTO profile = memberService.getActiveProfile(memberId);
        String title = growthService.loadLevelTable().titleOf(profile.level());

        // TODO(trading 의 수상 내역 조회 메서드가 공개되면 연결): 지금은 빈 목록
        return new PublicProfileDTO(profile.memberId(), profile.nickname(), profile.profileImageUrl(),
                profile.level(), title, List.of());
    }

    /**
     * 프로필 이미지 변경 (API 15). 2MB 이하 jpg·png 검사는 FileStorageService 가 하고, 맞지 않으면 FILE_TOO_LARGE·FILE_TYPE_NOT_ALLOWED 다.
     * 회원이 있는지 먼저 확인해서, 없는 회원 때문에 파일만 남지 않게 한다.
     */
    public ProfileImageDTO changeProfileImage(Long memberId, MultipartFile file) {
        memberService.getProfile(memberId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(CommonErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        String url;
        try {
            url = fileStorageService.saveImage(file);
        } catch (IOException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        return memberService.changeProfileImageUrl(memberId, url);
    }
}
