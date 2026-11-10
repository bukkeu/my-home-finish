package com.wanted.moneybeetle.domain.home.model.service;

import com.wanted.moneybeetle.domain.course.exception.CourseErrorCode;
import com.wanted.moneybeetle.domain.course.model.dto.CourseListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.WatchHistoryDTO;
import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.growth.model.service.UnlockRules;
import com.wanted.moneybeetle.domain.home.model.dto.HomeDTO;
import com.wanted.moneybeetle.domain.term.model.service.TermService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/* comment.
*   담당 : DBA ② (홈 API, #13) · PM (통합 검색 API, #15)
*   course / term / trading 서비스의 조회 메서드만 모아서 보여 준다 (다른 도메인 테이블 직접 조회 금지)
*   - 홈 화면 (API 19): 오늘의 경제 용어(term), 이어서 볼 강의·학습 기록(course), 트레이닝 해금(growth 의 레벨)을 한 번에 모은다.
*   - 이 클래스는 트랜잭션을 열지 않는다. 각 서비스 호출이 자기 트랜잭션을 연다 (오늘의 용어 자동 선정이 트랜잭션 밖에서 실행돼야 해서).
*   - 이어서 볼 강의는 course 도메인에 "강의 + 마지막 영상 + 위치"를 한 번에 주는 메서드가 없어서, 공개된 조회 메서드 세 개
*     (이어 볼 강의 목록, 시청 기록, 영상 재생 정보)를 조합한다. 전용 메서드가 생기면 그것으로 바꾼다.
*  */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeService {

    /** 이어서 볼 강의의 마지막 영상을 찾으려고 한 번에 읽는 시청 기록 개수 */
    static final int WATCH_SCAN_SIZE = 20;

    /** 마지막 영상을 찾을 때 넘겨 볼 최대 쪽 수 (20개씩 50쪽 = 최근 1,000건) */
    static final int MAX_WATCH_PAGES = 50;

    private final TermService termService;
    private final CourseService courseService;
    private final GrowthService growthService;

    public HomeDTO getHome(Long memberId) {
        var todayTerms = termService.getToday().terms();
        int level = growthService.getLevel(memberId);

        long watchedLessons = courseService.findWatchHistories(memberId, PageRequest.of(0, 1)).getTotalElements();
        long completedCourses = courseService.findCompletedCourses(memberId, PageRequest.of(0, 1)).getTotalElements();

        return new HomeDTO(
                todayTerms,
                findContinueCourse(memberId),
                new HomeDTO.Learning(watchedLessons, completedCourses),
                new HomeDTO.Training(
                        new HomeDTO.Unlock(level >= UnlockRules.TRADING_LEVEL, UnlockRules.TRADING_LEVEL),
                        new HomeDTO.Unlock(level >= UnlockRules.HOUSING_LEVEL, UnlockRules.HOUSING_LEVEL)));
    }

    /** 가장 최근에 본 진행 중인 강의 하나와 그 강의에서 마지막으로 본 영상·위치. 이어서 볼 강의가 없거나 그 사이 비공개가 되었으면 null */
    private HomeDTO.ContinueCourse findContinueCourse(Long memberId) {
        List<CourseListDTO> continuing = courseService.findContinueWatching(memberId);
        if (continuing.isEmpty()) {
            return null;
        }
        CourseListDTO course = continuing.get(0);

        WatchHistoryDTO lastWatched = findLastWatched(memberId, course.getCourseId());
        if (lastWatched == null) {
            return new HomeDTO.ContinueCourse(course.getCourseId(), course.getTitle(), null, null, null);
        }
        int position;
        try {
            position = courseService.getLessonPlay(memberId, course.getCourseId(), lastWatched.getLessonId())
                    .getPositionSeconds();
        } catch (BusinessException e) {
            // 이어서 볼 강의를 찾은 뒤 그 강의가 비공개로 바뀌는 경우: 홈 전체가 실패하지 않고 이어서 볼 강의만 비운다.
            // 그 밖의 오류는 숨기지 않고 그대로 던진다
            if (e.getErrorCode() == CourseErrorCode.COURSE_NOT_PUBLIC) {
                return null;
            }
            throw e;
        }
        return new HomeDTO.ContinueCourse(course.getCourseId(), course.getTitle(),
                lastWatched.getLessonId(), lastWatched.getLessonTitle(), position);
    }

    /**
     * 그 강의에서 가장 최근에 본 영상 기록. 시청 기록은 최근 순이라서 첫 번째로 맞는 것이 가장 최근이다.
     * 다른 강의 영상을 많이 본 뒤라면 첫 쪽에 없을 수 있으므로, 찾거나 마지막 쪽에 닿을 때까지 쪽을 넘겨 가며 찾는다.
     * 기록이 아주 많은 회원이 홈을 열 때마다 끝없이 읽지 않도록 최대 MAX_WATCH_PAGES 쪽까지만 본다.
     */
    private WatchHistoryDTO findLastWatched(Long memberId, Long courseId) {
        for (int pageNo = 0; pageNo < MAX_WATCH_PAGES; pageNo++) {
            Page<WatchHistoryDTO> page = courseService.findWatchHistories(memberId, PageRequest.of(pageNo, WATCH_SCAN_SIZE));
            for (WatchHistoryDTO history : page.getContent()) {
                if (courseId.equals(history.getCourseId())) {
                    return history;
                }
            }
            if (!page.hasNext()) {
                return null;
            }
        }
        // 상한까지 읽고도 끝나지 않았다: 그 강의의 기록이 더 오래됐거나 기록이 아주 많은 회원이다. 영상·위치 없이 강의만 보여 준다
        log.warn("이어서 볼 강의의 마지막 영상을 최근 {}건 안에서 찾지 못했어요: memberId={}, courseId={}",
                MAX_WATCH_PAGES * WATCH_SCAN_SIZE, memberId, courseId);
        return null;
    }
}
