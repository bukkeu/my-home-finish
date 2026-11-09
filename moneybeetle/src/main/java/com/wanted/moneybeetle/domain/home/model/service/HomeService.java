package com.wanted.moneybeetle.domain.home.model.service;

import com.wanted.moneybeetle.domain.course.model.dto.CourseListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.WatchHistoryDTO;
import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.growth.model.service.UnlockRules;
import com.wanted.moneybeetle.domain.home.model.dto.HomeDTO;
import com.wanted.moneybeetle.domain.term.model.service.TermService;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class HomeService {

    /** 이어서 볼 강의의 마지막 영상을 찾으려고 최근 시청 기록을 훑는 개수 */
    static final int WATCH_SCAN_SIZE = 20;

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

    /** 가장 최근에 본 진행 중인 강의 하나와 그 강의에서 마지막으로 본 영상·위치. 이어서 볼 강의가 없으면 null */
    private HomeDTO.ContinueCourse findContinueCourse(Long memberId) {
        List<CourseListDTO> continuing = courseService.findContinueWatching(memberId);
        if (continuing.isEmpty()) {
            return null;
        }
        CourseListDTO course = continuing.get(0);

        WatchHistoryDTO lastWatched = courseService.findWatchHistories(memberId, PageRequest.of(0, WATCH_SCAN_SIZE))
                .getContent().stream()
                .filter(history -> course.getCourseId().equals(history.getCourseId()))
                .findFirst()
                .orElse(null);
        if (lastWatched == null) {
            return new HomeDTO.ContinueCourse(course.getCourseId(), course.getTitle(), null, null, null);
        }
        int position = courseService.getLessonPlay(memberId, course.getCourseId(), lastWatched.getLessonId())
                .getPositionSeconds();
        return new HomeDTO.ContinueCourse(course.getCourseId(), course.getTitle(),
                lastWatched.getLessonId(), lastWatched.getLessonTitle(), position);
    }
}
